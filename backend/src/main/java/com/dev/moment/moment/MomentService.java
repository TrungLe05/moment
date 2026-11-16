package com.dev.moment.moment;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.common.error.FieldViolation;
import com.dev.moment.friend.FriendService;
import com.dev.moment.job.MediaJobService;
import com.dev.moment.media.MediaRules;
import com.dev.moment.media.ObjectStorage;
import com.dev.moment.user.UserService;
import com.dev.moment.user.UserSummary;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tạo khoảnh khắc, báo tải xong, xử lý lại (T-131), xem chi tiết (T-141); API mục 3.4.
 * Gọi object storage luôn nằm ngoài transaction database (CLAUDE.md).
 */
@Service
class MomentService {

    /** Cửa sổ feed 24 giờ tính từ lúc chủ bấm gửi (BR-13). */
    static final Duration FEED_WINDOW = Duration.ofHours(24);
    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(10);
    private static final short CURRENT_LAYERS_VERSION = 1;

    private final MomentRepository moments;
    private final MomentMediaRepository media;
    private final MomentRecipientRepository recipients;
    private final FriendService friendService;
    private final UserService userService;
    private final MediaJobService mediaJobService;
    private final MomentAccessPolicy accessPolicy;
    private final ObjectStorage storage;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    MomentService(MomentRepository moments, MomentMediaRepository media,
            MomentRecipientRepository recipients, FriendService friendService, UserService userService,
            MediaJobService mediaJobService, MomentAccessPolicy accessPolicy, ObjectStorage storage,
            EntityManager entityManager,
            TransactionTemplate transactionTemplate, JsonMapper jsonMapper, Clock clock) {
        this.moments = moments;
        this.media = media;
        this.recipients = recipients;
        this.friendService = friendService;
        this.userService = userService;
        this.mediaJobService = mediaJobService;
        this.accessPolicy = accessPolicy;
        this.storage = storage;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    MomentResponse.Created create(UUID ownerId, CreateMomentRequest request) {
        Map<MomentMedia.Variant, String> extensions = checkFiles(request);
        checkTrim(request);
        List<CreateMomentRequest.Layer> layers = request.layers() == null ? List.of() : request.layers();
        List<FieldViolation> violations = LayerRules.check(request.caption(), layers);
        if (request.layersVersion() != null && request.layersVersion() != CURRENT_LAYERS_VERSION) {
            violations.add(FieldViolation.of("layersVersion", "INVALID"));
        }
        if (!violations.isEmpty()) {
            throw ApiException.withViolations(ErrorCode.VALIDATION_FAILED, violations);
        }
        Set<UUID> chosenRecipients = chooseRecipients(ownerId, request);

        UUID momentId = UUID.randomUUID();
        Instant now = clock.instant();
        List<MomentMedia> files = request.files().stream()
                .map(file -> new MomentMedia(momentId, file.variant(),
                        MomentMedia.objectKey(momentId, file.variant(), extensions.get(file.variant())),
                        file.contentType(), file.sizeBytes(), now))
                .toList();
        Moment moment = new Moment(momentId, ownerId, request.mediaType(), request.source(), request.mode(),
                request.audience(), LayerRules.captionText(request.caption(), layers),
                jsonMapper.writeValueAsString(layers), CURRENT_LAYERS_VERSION, request.trimStartMs(), now,
                now.plus(FEED_WINDOW));
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(moment);
            chosenRecipients.forEach(recipientId -> entityManager.persist(
                    new MomentRecipient(momentId, recipientId, now)));
            files.forEach(entityManager::persist);
        });

        List<MomentResponse.Upload> uploads = files.stream()
                .map(file -> {
                    ObjectStorage.PresignedUpload upload = storage.presignUpload(file.getObjectKey(),
                            file.getContentType(), file.getSizeBytes(), UPLOAD_URL_TTL);
                    return new MomentResponse.Upload(file.getVariant().name(), upload.method(), upload.url(),
                            upload.headers(), upload.expiresAt());
                })
                .toList();
        return new MomentResponse.Created(toResponse(moment, true, chosenRecipients.size(), files), uploads);
    }

    /** US-28: kiểm tra từng file đã có trên storage, đúng dung lượng khai báo, rồi bắt đầu xử lý. */
    MomentResponse complete(UUID ownerId, UUID momentId) {
        Moment moment = ownedMoment(ownerId, momentId);
        if (moment.getStatus() != Moment.Status.DRAFT) {
            throw new ApiException(ErrorCode.MOMENT_NOT_DRAFT);
        }
        List<MomentMedia> files = media.findAllByMomentId(momentId);
        for (MomentMedia file : files) {
            Optional<ObjectStorage.StoredObject> stored = storage.find(file.getObjectKey());
            if (stored.isEmpty() || stored.get().sizeBytes() != file.getSizeBytes()) {
                throw new ApiException(ErrorCode.UPLOAD_INCOMPLETE);
            }
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (moments.transition(momentId, ownerId, Moment.Status.DRAFT, Moment.Status.PROCESSING) == 0) {
                throw new ApiException(ErrorCode.MOMENT_NOT_DRAFT);
            }
            mediaJobService.enqueueProcessMoment(momentId);
        });
        return currentResponse(ownerId, momentId);
    }

    /** US-28: xử lý thất bại thì chủ thử lại; chỉ cho trạng thái FAILED. */
    MomentResponse retry(UUID ownerId, UUID momentId) {
        ownedMoment(ownerId, momentId);
        transactionTemplate.executeWithoutResult(status -> {
            if (moments.transition(momentId, ownerId, Moment.Status.FAILED, Moment.Status.PROCESSING) == 0) {
                throw new ApiException(ErrorCode.MOMENT_NOT_FAILED);
            }
            mediaJobService.enqueueProcessMoment(momentId);
        });
        return currentResponse(ownerId, momentId);
    }

    /** US-42: chi tiết một khoảnh khắc; chỉ trả khi người xem có quyền (BR-19, BR-20). */
    MomentResponse detail(UUID viewerId, UUID momentId) {
        return responseFor(viewerId, accessPolicy.viewable(viewerId, momentId));
    }

    // Không phải chủ thì trả 404 như khoảnh khắc không tồn tại (BR-20).
    private Moment ownedMoment(UUID ownerId, UUID momentId) {
        return moments.findById(momentId)
                .filter(found -> found.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private MomentResponse currentResponse(UUID ownerId, UUID momentId) {
        entityManager.clear();
        return responseFor(ownerId, ownedMoment(ownerId, momentId));
    }

    // Số người nhận chỉ chủ mới thấy (ownerView), nên chỉ đếm khi người xem là chủ.
    private MomentResponse responseFor(UUID viewerId, Moment moment) {
        boolean mine = moment.getOwnerId().equals(viewerId);
        int recipientCount = mine ? (int) recipients.countByMomentId(moment.getId()) : 0;
        return toResponse(moment, mine, recipientCount, media.findAllByMomentId(moment.getId()));
    }

    /** BR-08, BR-10: ảnh cần ORIGINAL + COMPOSED, video cần đúng một ORIGINAL; định dạng và dung lượng theo loại. */
    private static Map<MomentMedia.Variant, String> checkFiles(CreateMomentRequest request) {
        Set<MomentMedia.Variant> expected = request.mediaType() == Moment.MediaType.PHOTO
                ? Set.of(MomentMedia.Variant.ORIGINAL, MomentMedia.Variant.COMPOSED)
                : Set.of(MomentMedia.Variant.ORIGINAL);
        Set<MomentMedia.Variant> declared = request.files().stream()
                .map(CreateMomentRequest.FileDeclaration::variant)
                .collect(Collectors.toSet());
        if (declared.size() != request.files().size() || !declared.equals(expected)) {
            throw ApiException.withViolations(ErrorCode.VALIDATION_FAILED, List.of(FieldViolation.of("files", "INVALID")));
        }
        Map<MomentMedia.Variant, String> extensions = new EnumMap<>(MomentMedia.Variant.class);
        for (CreateMomentRequest.FileDeclaration file : request.files()) {
            boolean video = request.mediaType() == Moment.MediaType.VIDEO;
            Optional<String> extension = file.variant() == MomentMedia.Variant.COMPOSED
                    ? MediaRules.composedExtension(file.contentType())
                    : video ? MediaRules.videoExtension(file.contentType()) : MediaRules.photoExtension(file.contentType());
            if (extension.isEmpty()) {
                throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
            if (file.sizeBytes() <= 0) {
                throw ApiException.withViolations(ErrorCode.VALIDATION_FAILED,
                        List.of(FieldViolation.of("files", "INVALID")));
            }
            if (file.sizeBytes() > (video ? MediaRules.MAX_VIDEO_BYTES : MediaRules.MAX_PHOTO_BYTES)) {
                throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
            }
            extensions.put(file.variant(), extension.get());
        }
        return extensions;
    }

    // BR-10: điểm bắt đầu chỉ dùng cho video tải lên; độ dài thật kiểm tra ở worker sau khi xử lý.
    private static void checkTrim(CreateMomentRequest request) {
        boolean allowed = request.mediaType() == Moment.MediaType.VIDEO && request.source() == Moment.Source.UPLOAD;
        if (request.trimStartMs() != null && !allowed) {
            throw ApiException.withViolations(ErrorCode.VALIDATION_FAILED,
                    List.of(FieldViolation.of("trimStartMs", "INVALID")));
        }
    }

    /** BR-12: "tất cả bạn bè" hoặc danh sách chọn từ bạn hiện tại, ít nhất 1 người; chốt lúc đăng. */
    private Set<UUID> chooseRecipients(UUID ownerId, CreateMomentRequest request) {
        Set<UUID> friends = friendService.friendIds(ownerId);
        if (friends.isEmpty()) {
            throw new ApiException(ErrorCode.NO_FRIENDS);
        }
        if (request.audience() == Moment.Audience.ALL) {
            return friends;
        }
        List<UUID> selected = request.recipientIds() == null ? List.of() : request.recipientIds();
        if (selected.isEmpty()) {
            throw ApiException.withViolations(ErrorCode.VALIDATION_FAILED,
                    List.of(FieldViolation.of("recipientIds", "REQUIRED")));
        }
        List<FieldViolation> notFriends = new ArrayList<>();
        for (UUID recipientId : selected) {
            if (!friends.contains(recipientId)) {
                notFriends.add(new FieldViolation("recipientIds", "NOT_FRIENDS", Map.of("id", recipientId)));
            }
        }
        if (!notFriends.isEmpty()) {
            throw ApiException.withViolations(ErrorCode.NOT_FRIENDS, notFriends);
        }
        return Set.copyOf(selected);
    }

    private MomentResponse toResponse(Moment moment, boolean mine, int recipientCount, List<MomentMedia> files) {
        UserSummary owner = userService.findSummary(moment.getOwnerId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        // API mục 4: ownerView chỉ có với chủ. Đếm reaction và myReaction làm cùng T-143.
        MomentResponse.OwnerView ownerView = mine
                ? new MomentResponse.OwnerView(moment.getAudience().name(), recipientCount, 0, moment.getFailureReason())
                : null;
        return new MomentResponse(moment.getId(), mine, owner, moment.getMediaType().name(), moment.getSource().name(),
                moment.getMode().name(), moment.getStatus().name(), moment.getCaption(),
                jsonMapper.readTree(moment.getLayers()), moment.getLayersVersion(), moment.getWidth(),
                moment.getHeight(), moment.getDurationMs(), moment.getCreatedAt(), moment.getExpiresAt(),
                moment.getKeptAt(), mediaUrls(moment, files), null, ownerView);
    }

    // URL đọc file chỉ cấp khi khoảnh khắc đã sẵn sàng (API mục 4).
    private MomentResponse.MediaUrls mediaUrls(Moment moment, List<MomentMedia> files) {
        if (moment.getStatus() != Moment.Status.READY) {
            return null;
        }
        Map<MomentMedia.Variant, ObjectStorage.PresignedDownload> urls = new EnumMap<>(MomentMedia.Variant.class);
        for (MomentMedia file : files) {
            urls.put(file.getVariant(), storage.presignDownload(file.getObjectKey(), DOWNLOAD_URL_TTL));
        }
        return new MomentResponse.MediaUrls(urlOf(urls, MomentMedia.Variant.DISPLAY),
                urlOf(urls, MomentMedia.Variant.VIDEO), urlOf(urls, MomentMedia.Variant.THUMBNAIL),
                clock.instant().plus(DOWNLOAD_URL_TTL));
    }

    private static String urlOf(Map<MomentMedia.Variant, ObjectStorage.PresignedDownload> urls,
            MomentMedia.Variant variant) {
        ObjectStorage.PresignedDownload download = urls.get(variant);
        return download == null ? null : download.url();
    }
}
