package com.dev.moment.moment;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.friend.FriendService;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Quyền xem khoảnh khắc, một chỗ duy nhất cho mọi API (ARCHITECTURE.md mục 2, DB mục 8).
 * Không có quyền thì trả 404 như khoảnh khắc không tồn tại, để không lộ nó có hay không (BR-20).
 */
@Component
class MomentAccessPolicy {

    private final MomentRepository moments;
    private final MomentRecipientRepository recipients;
    private final FriendService friendService;
    private final Clock clock;

    MomentAccessPolicy(MomentRepository moments, MomentRecipientRepository recipients, FriendService friendService,
            Clock clock) {
        this.moments = moments;
        this.recipients = recipients;
        this.friendService = friendService;
        this.clock = clock;
    }

    /**
     * BR-19: chủ luôn xem được. Người khác xem được khi nằm trong danh sách người nhận, đang là bạn của chủ,
     * và khoảnh khắc còn trong 24 giờ (trước expires_at) hoặc ở chế độ lưu giữ.
     * BR-13: người nhận chỉ thấy khi đã xử lý xong.
     */
    boolean canView(UUID viewerId, Moment moment) {
        if (moment.getOwnerId().equals(viewerId)) {
            return true;
        }
        if (moment.getStatus() != Moment.Status.READY) {
            return false;
        }
        boolean inFeedWindow = clock.instant().isBefore(moment.getExpiresAt());
        if (moment.getMode() != Moment.Mode.KEPT && !inFeedWindow) {
            return false;
        }
        return recipients.existsById(new MomentRecipient.Key(moment.getId(), viewerId))
                && friendService.areFriends(moment.getOwnerId(), viewerId);
    }

    /** Khoảnh khắc người xem có quyền xem, hoặc lỗi NOT_FOUND. */
    Moment viewable(UUID viewerId, UUID momentId) {
        return moments.findById(momentId)
                .filter(moment -> canView(viewerId, moment))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /**
     * Một trang feed theo cùng quy tắc với {@link #canView} nhưng lọc ngay trong câu truy vấn (ARCHITECTURE.md mục 4),
     * thêm điều kiện còn trước expires_at cho cả khoảnh khắc lưu giữ và của chính mình (US-30, BR-15).
     */
    List<Moment> feedPage(UUID viewerId, FeedCursor after, int limit) {
        Set<UUID> owners = new HashSet<>(friendService.friendIds(viewerId));
        owners.add(viewerId);
        return after == null
                ? moments.feedFirstPage(viewerId, owners, clock.instant(), limit)
                : moments.feedPageAfter(viewerId, owners, clock.instant(), after.createdAt(), after.id(), limit);
    }
}
