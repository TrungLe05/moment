package com.dev.moment.moment;

import com.dev.moment.job.MediaJobHandler;
import com.dev.moment.job.MediaJobService;
import com.dev.moment.job.MediaJobType;
import com.dev.moment.job.NonRetryableJobException;
import com.dev.moment.media.FfmpegTranscoder;
import com.dev.moment.media.FfmpegTranscoder.MediaInfo;
import com.dev.moment.media.MediaRules;
import com.dev.moment.media.ObjectStorage;
import com.dev.moment.media.UnreadableMediaException;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Việc PROCESS_MOMENT (T-132): tạo DISPLAY, VIDEO, THUMBNAIL theo DB mục 6 rồi chuyển khoảnh khắc sang READY.
 * Tải file, ffmpeg và ghi storage nằm ngoài transaction; chỉ bước cuối ghi database (CLAUDE.md).
 */
@Component
class MomentProcessor implements MediaJobHandler {

    /** Lý do thất bại lưu ở failure_reason (D-033). */
    static final String MEDIA_UNREADABLE = "MEDIA_UNREADABLE";
    static final String VIDEO_TOO_LONG = "VIDEO_TOO_LONG";

    static final int DISPLAY_LONG_SIDE = 1440;
    static final int THUMBNAIL_LONG_SIDE = 480;
    static final int VIDEO_SHORT_SIDE = 720;
    /** BR-08: video sau xử lý dài tối đa 5 giây. */
    static final long CLIP_LENGTH_MS = 5_000;
    /** BR-10: video tải lên tối đa 60 giây. */
    static final long MAX_SOURCE_VIDEO_MS = 60_000;

    private static final String JPEG = "image/jpeg";
    private static final String MP4 = "video/mp4";
    private static final Logger log = LoggerFactory.getLogger(MomentProcessor.class);

    private final MomentRepository moments;
    private final MomentMediaRepository media;
    private final MediaJobService mediaJobService;
    private final ObjectStorage storage;
    private final FfmpegTranscoder transcoder;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    MomentProcessor(MomentRepository moments, MomentMediaRepository media, MediaJobService mediaJobService,
            ObjectStorage storage, FfmpegTranscoder transcoder, EntityManager entityManager,
            TransactionTemplate transactionTemplate, Clock clock) {
        this.moments = moments;
        this.media = media;
        this.mediaJobService = mediaJobService;
        this.storage = storage;
        this.transcoder = transcoder;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Override
    public MediaJobType type() {
        return MediaJobType.PROCESS_MOMENT;
    }

    private record Work(Moment moment, List<MomentMedia> files) {

        MomentMedia file(MomentMedia.Variant variant) {
            return files.stream().filter(file -> file.getVariant() == variant).findFirst()
                    .orElseThrow(() -> new NonRetryableJobException(MEDIA_UNREADABLE, "Thiếu file " + variant));
        }
    }

    /** Một file do worker tạo, đã nằm trên đĩa và chờ ghi lên storage. */
    private record Output(MomentMedia.Variant variant, Path path, String contentType, String extension,
            MediaInfo info) {
    }

    @Override
    public void handle(UUID momentId, JsonNode payload) {
        Optional<Work> work = transactionTemplate.execute(status -> moments.findById(momentId)
                .filter(moment -> moment.getStatus() == Moment.Status.PROCESSING)
                .map(moment -> new Work(moment, media.findAllByMomentId(momentId))));
        if (work == null || work.isEmpty()) {
            // Đã xóa, hết hạn hoặc xử lý xong ở lần chạy trước: không còn gì để làm.
            log.info("Bỏ qua xử lý khoảnh khắc {}: không còn ở trạng thái PROCESSING", momentId);
            return;
        }
        Path directory = createWorkDirectory();
        try {
            List<Output> outputs = work.get().moment().getMediaType() == Moment.MediaType.PHOTO
                    ? processPhoto(work.get(), directory)
                    : processVideo(work.get(), directory);
            publish(work.get(), outputs);
        } catch (UnreadableMediaException unreadable) {
            throw new NonRetryableJobException(MEDIA_UNREADABLE, unreadable.getMessage(), unreadable);
        } finally {
            deleteRecursively(directory);
        }
    }

    /** Gọi trong transaction đánh dấu việc thất bại (MediaWorker). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void onFailed(UUID momentId, String reason) {
        moments.markFailed(momentId, reason);
    }

    // ADR 0002: DISPLAY và THUMBNAIL tạo từ bản ghép sẵn lớp trang trí; ảnh gốc giữ nguyên.
    // THUMBNAIL thu nhỏ tiếp từ DISPLAY để không phải giải mã ảnh ghép cỡ lớn hai lần.
    private List<Output> processPhoto(Work work, Path directory) {
        MomentMedia composed = work.file(MomentMedia.Variant.COMPOSED);
        Path input = download(composed, directory, MediaRules.composedExtension(composed.getContentType()));
        transcoder.probe(input, composed.getContentType());
        Path display = directory.resolve("display.jpg");
        Path thumbnail = directory.resolve("thumbnail.jpg");
        MediaInfo displayInfo = transcoder.resizeImage(input, composed.getContentType(), display, DISPLAY_LONG_SIDE);
        MediaInfo thumbnailInfo = transcoder.resizeImage(display, JPEG, thumbnail, THUMBNAIL_LONG_SIDE);
        return List.of(new Output(MomentMedia.Variant.DISPLAY, display, JPEG, "jpg", displayInfo),
                new Output(MomentMedia.Variant.THUMBNAIL, thumbnail, JPEG, "jpg", thumbnailInfo));
    }

    private List<Output> processVideo(Work work, Path directory) {
        MomentMedia original = work.file(MomentMedia.Variant.ORIGINAL);
        Path input = download(original, directory, MediaRules.videoExtension(original.getContentType()));
        MediaInfo source = transcoder.probe(input, original.getContentType());
        if (source.durationMs() == null || source.durationMs() <= 0) {
            throw new UnreadableMediaException("Không đọc được thời lượng video");
        }
        if (source.durationMs() > MAX_SOURCE_VIDEO_MS) {
            throw new NonRetryableJobException(VIDEO_TOO_LONG, "Video dài " + source.durationMs() + " ms");
        }
        long startMs = clipStart(work.moment().getTrimStartMs(), source.durationMs());
        long lengthMs = Math.min(CLIP_LENGTH_MS, source.durationMs() - startMs);
        Path video = directory.resolve("video.mp4");
        Path thumbnail = directory.resolve("thumbnail.jpg");
        MediaInfo videoInfo = transcoder.transcodeVideo(input, original.getContentType(), video, startMs, lengthMs,
                VIDEO_SHORT_SIDE);
        MediaInfo thumbnailInfo = transcoder.firstFrame(video, thumbnail, THUMBNAIL_LONG_SIDE);
        return List.of(new Output(MomentMedia.Variant.VIDEO, video, MP4, "mp4", videoInfo),
                new Output(MomentMedia.Variant.THUMBNAIL, thumbnail, JPEG, "jpg", thumbnailInfo));
    }

    /**
     * BR-10, D-033: cắt 5 giây từ điểm bắt đầu; không có điểm bắt đầu thì lấy từ đầu.
     * Điểm bắt đầu quá gần cuối thì lùi lại để đoạn cắt vẫn đủ 5 giây khi video đủ dài.
     */
    static long clipStart(Integer trimStartMs, long durationMs) {
        long requested = trimStartMs == null ? 0 : trimStartMs;
        return Math.max(0, Math.min(requested, durationMs - CLIP_LENGTH_MS));
    }

    /** Ghi file lên storage rồi chốt READY trong một transaction; khoảnh khắc đổi trạng thái giữa chừng thì dọn file. */
    private void publish(Work work, List<Output> outputs) {
        UUID momentId = work.moment().getId();
        List<String> uploaded = new ArrayList<>();
        boolean finished;
        try {
            List<MomentMedia> rows = new ArrayList<>();
            for (Output output : outputs) {
                String key = MomentMedia.objectKey(momentId, output.variant(), output.extension());
                storage.upload(key, output.path(), output.contentType());
                uploaded.add(key);
                rows.add(new MomentMedia(momentId, output.variant(), key, output.contentType(),
                        sizeOf(output.path()), output.info().width(), output.info().height(),
                        output.info().durationMs(), clock.instant()));
            }
            // Kích thước của khoảnh khắc là của file hiển thị chính (DISPLAY với ảnh, VIDEO với video).
            MediaInfo main = outputs.getFirst().info();
            Boolean ready = transactionTemplate.execute(status -> {
                if (moments.markReady(momentId, main.width(), main.height(), main.durationMs(), clock.instant()) == 0) {
                    return false;
                }
                rows.forEach(entityManager::persist);
                if (work.moment().getMediaType() == Moment.MediaType.VIDEO) {
                    dropOriginalVideo(work);
                }
                return true;
            });
            finished = Boolean.TRUE.equals(ready);
        } catch (RuntimeException exception) {
            deleteQuietly(uploaded, exception);
            throw exception;
        }
        if (!finished) {
            log.info("Khoảnh khắc {} đổi trạng thái trong lúc xử lý, xóa file vừa tạo", momentId);
            storage.deleteAll(uploaded);
        }
    }

    // DB mục 6: video gốc xóa sau khi xử lý xong; file trên storage xóa qua hàng đợi sau khi transaction commit.
    private void dropOriginalVideo(Work work) {
        MomentMedia original = work.file(MomentMedia.Variant.ORIGINAL);
        media.deleteById(original.getId());
        mediaJobService.enqueueDeleteObjects(work.moment().getId(), List.of(original.getObjectKey()));
    }

    private Path download(MomentMedia file, Path directory, Optional<String> extension) {
        // Đuôi file suy từ Content-Type đã kiểm tra lúc tạo, không lấy từ tên file client gửi (CLAUDE.md).
        String suffix = extension.orElseThrow(() -> new NonRetryableJobException(MEDIA_UNREADABLE,
                "Định dạng không hỗ trợ: " + file.getContentType()));
        Path target = directory.resolve("input." + suffix);
        storage.download(file.getObjectKey(), target);
        return target;
    }

    private void deleteQuietly(List<String> keys, RuntimeException cause) {
        if (keys.isEmpty()) {
            return;
        }
        try {
            storage.deleteAll(keys);
        } catch (RuntimeException cleanup) {
            cause.addSuppressed(cleanup);
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path createWorkDirectory() {
        try {
            return Files.createTempDirectory("moment-job-");
        } catch (IOException exception) {
            throw new UncheckedIOException("Không tạo được thư mục tạm", exception);
        }
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            log.warn("Không xóa được thư mục tạm {}", directory, exception);
        }
    }
}
