package com.dev.moment.moment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MomentRepository extends JpaRepository<Moment, UUID> {

    // Chuyển trạng thái có điều kiện trong một câu lệnh, nên hai request complete cùng lúc chỉ một cái thắng.
    @Modifying
    @Query("""
            update Moment m set m.status = :to, m.failureReason = null
            where m.id = :id and m.ownerId = :ownerId and m.status = :from
            """)
    int transition(@Param("id") UUID id, @Param("ownerId") UUID ownerId, @Param("from") Moment.Status from,
            @Param("to") Moment.Status to);

    // Worker xong việc: chỉ đổi khi còn PROCESSING, nên khoảnh khắc đã hết hạn hay đã xóa giữa chừng không sống lại.
    @Modifying
    @Query("""
            update Moment m set m.status = com.dev.moment.moment.Moment.Status.READY, m.width = :width,
                m.height = :height, m.durationMs = :durationMs, m.processedAt = :now, m.failureReason = null
            where m.id = :id and m.status = com.dev.moment.moment.Moment.Status.PROCESSING
            """)
    int markReady(@Param("id") UUID id, @Param("width") int width, @Param("height") int height,
            @Param("durationMs") Integer durationMs, @Param("now") Instant now);

    @Modifying
    @Query("""
            update Moment m set m.status = com.dev.moment.moment.Moment.Status.FAILED, m.failureReason = :reason
            where m.id = :id and m.status = com.dev.moment.moment.Moment.Status.PROCESSING
            """)
    int markFailed(@Param("id") UUID id, @Param("reason") String reason);

    // Feed (DB mục 7, BR-13, BR-19): khoảnh khắc READY còn trước expires_at, của tôi hoặc gửi cho tôi từ người đang là bạn.
    // Danh sách bạn (tối đa 15, BR-06) lấy qua FriendService để gói moment không đọc thẳng bảng friendships.
    // Luôn có ít nhất id của chính mình trong :friendIds để mệnh đề IN không rỗng.
    String FEED_CONDITIONS = """
            m.status = 'READY' AND m.expires_at > :now
            AND (m.owner_id = :me OR (m.owner_id IN (:friendIds) AND EXISTS (
                SELECT 1 FROM moment_recipients r WHERE r.moment_id = m.id AND r.recipient_id = :me)))
            """;

    @Query(value = "SELECT m.* FROM moments m WHERE " + FEED_CONDITIONS
            + " ORDER BY m.created_at DESC, m.id DESC LIMIT :limit", nativeQuery = true)
    List<Moment> feedFirstPage(@Param("me") UUID me, @Param("friendIds") Collection<UUID> friendIds,
            @Param("now") Instant now, @Param("limit") int limit);

    @Query(value = "SELECT m.* FROM moments m WHERE " + FEED_CONDITIONS
            + " AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)"
            + " ORDER BY m.created_at DESC, m.id DESC LIMIT :limit", nativeQuery = true)
    List<Moment> feedPageAfter(@Param("me") UUID me, @Param("friendIds") Collection<UUID> friendIds,
            @Param("now") Instant now, @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId, @Param("limit") int limit);
}
