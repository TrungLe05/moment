package com.dev.moment.reaction;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.common.error.FieldViolation;
import com.dev.moment.moment.MomentAccessPolicy;
import com.dev.moment.user.UserService;
import com.dev.moment.user.UserSummary;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thả, đổi, gỡ và xem reaction (T-143, API mục 3.5). Quyền xem luôn hỏi qua MomentAccessPolicy (BR-24). */
@Service
public class ReactionService {

    // Mã SQLState chuẩn của PostgreSQL cho vi phạm khóa ngoại.
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private final ReactionRepository reactions;
    private final MomentAccessPolicy accessPolicy;
    private final UserService userService;
    private final Clock clock;

    ReactionService(ReactionRepository reactions, MomentAccessPolicy accessPolicy, UserService userService,
            Clock clock) {
        this.reactions = reactions;
        this.accessPolicy = accessPolicy;
        this.userService = userService;
        this.clock = clock;
    }

    /** US-32: đặt hoặc đổi reaction của tôi; trả emoji đã chuẩn hóa (D-028). */
    @Transactional
    String react(UUID userId, UUID momentId, String emoji) {
        String canonical = ReactionEmoji.normalize(emoji).orElseThrow(() -> ApiException.withViolations(
                ErrorCode.VALIDATION_FAILED, List.of(FieldViolation.of("emoji", "INVALID"))));
        UUID ownerId = accessPolicy.ownerOfViewable(userId, momentId);
        // BR-23: chủ không tự thả reaction.
        if (ownerId.equals(userId)) {
            throw new ApiException(ErrorCode.REACTION_OWN_MOMENT);
        }
        try {
            reactions.upsert(momentId, userId, canonical, clock.instant());
        } catch (DataIntegrityViolationException violation) {
            // Khoảnh khắc bị xóa giữa lúc kiểm tra quyền và lúc ghi (vi phạm khóa ngoại): coi như không tồn tại.
            // Vi phạm khác (ví dụ CHECK emoji lệch với ReactionEmoji) là lỗi code, để lỗi 500 lộ ra.
            if (isForeignKeyViolation(violation)) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
            throw violation;
        }
        return canonical;
    }

    private static boolean isForeignKeyViolation(DataIntegrityViolationException violation) {
        return NestedExceptionUtils.getMostSpecificCause(violation) instanceof SQLException sql
                && FOREIGN_KEY_VIOLATION.equals(sql.getSQLState());
    }

    /** US-32: gỡ reaction của tôi; chưa thả cũng trả thành công (API mục 3.5). */
    @Transactional
    void unreact(UUID userId, UUID momentId) {
        accessPolicy.ownerOfViewable(userId, momentId);
        reactions.deleteMine(momentId, userId);
    }

    /** US-33, BR-22: chỉ chủ thấy ai đã thả gì, mới nhất trước; người khác nhận 404. */
    @Transactional(readOnly = true)
    List<ReactionResponses.Item> list(UUID viewerId, UUID momentId) {
        UUID ownerId = accessPolicy.ownerOfViewable(viewerId, momentId);
        if (!ownerId.equals(viewerId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        List<Reaction> found = reactions.findAllForMoment(momentId);
        Map<UUID, UserSummary> users = userService.findSummaries(
                found.stream().map(Reaction::getUserId).collect(Collectors.toSet()));
        return found.stream()
                .filter(reaction -> users.containsKey(reaction.getUserId()))
                .map(reaction -> new ReactionResponses.Item(users.get(reaction.getUserId()), reaction.getEmoji(),
                        reaction.getUpdatedAt()))
                .toList();
    }

    /** Reaction của một người trên nhiều khoảnh khắc (myReaction trong feed), một truy vấn. */
    @Transactional(readOnly = true)
    public Map<UUID, String> myReactions(UUID userId, Collection<UUID> momentIds) {
        if (momentIds.isEmpty()) {
            return Map.of();
        }
        return reactions.findMine(userId, momentIds).stream()
                .collect(Collectors.toMap(Reaction::getMomentId, Reaction::getEmoji));
    }

    /** Số reaction của nhiều khoảnh khắc (reactionCount cho chủ), một truy vấn. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> counts(Collection<UUID> momentIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!momentIds.isEmpty()) {
            for (Object[] row : reactions.countByMomentIds(momentIds)) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
        }
        return counts;
    }
}
