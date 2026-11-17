package com.dev.moment.moment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    // BR-16, DB mục 7: một câu lệnh có điều kiện nên không đụng độ với job hết hạn; 0 dòng thì không lưu giữ được.
    @Modifying
    @Query("""
            update Moment m set m.mode = com.dev.moment.moment.Moment.Mode.KEPT, m.keptAt = :now
            where m.id = :id and m.ownerId = :ownerId and m.mode = com.dev.moment.moment.Moment.Mode.SHARED
              and m.status in (com.dev.moment.moment.Moment.Status.PROCESSING, com.dev.moment.moment.Moment.Status.READY)
              and m.expiresAt > :now
            """)
    int keep(@Param("id") UUID id, @Param("ownerId") UUID ownerId, @Param("now") Instant now);

    // Khóa dòng khoảnh khắc trước khi xóa: worker đang chốt READY (markReady) phải chờ, nên không có file nào
    // được ghi thêm sau lúc đọc danh sách file cần xóa.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Moment m where m.id = :id and m.ownerId = :ownerId")
    Optional<Moment> lockOwned(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    // BR-18: xóa hẳn ở mọi trạng thái; người nhận, file và reaction xóa theo khóa ngoại ON DELETE CASCADE.
    @Modifying
    @Query("delete from Moment m where m.id = :id and m.ownerId = :ownerId")
    int deleteOwned(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    // Job định kỳ (DB mục 9): khóa một lô dòng bằng SKIP LOCKED để không chờ request đang giữ dòng (lưu giữ, xóa,
    // thử lại) và hai lượt job chồng nhau không lấy trùng. Khóa giữ tới hết transaction của bên gọi.

    // BR-14, DB mục 4: khoảnh khắc chia sẻ quá expires_at ở PROCESSING, READY hay FAILED sang EXPIRED.
    // Lệnh lưu giữ chạy cùng lúc phải chờ khóa, rồi thấy đã quá expires_at nên không lưu giữ được (BR-16).
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            SELECT id FROM moments
            WHERE mode = 'SHARED' AND status IN ('PROCESSING', 'READY', 'FAILED') AND expires_at <= :now
            ORDER BY expires_at LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> lockExpiredShared(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query("""
            update Moment m set m.status = com.dev.moment.moment.Moment.Status.EXPIRED
            where m.id in :ids and m.mode = com.dev.moment.moment.Moment.Mode.SHARED
            """)
    int markExpired(@Param("ids") Collection<UUID> ids);

    // DB mục 4: bản nháp chưa báo tải xong; D-029: khoảnh khắc FAILED ở mọi chế độ. Cả hai tính từ created_at.
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            SELECT id FROM moments WHERE status = :status AND created_at <= :createdBefore
            ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> lockStale(@Param("status") String status, @Param("createdBefore") Instant createdBefore,
            @Param("limit") int limit);

    // Người nhận, file và reaction xóa theo khóa ngoại ON DELETE CASCADE.
    @Modifying
    @Query("delete from Moment m where m.id in :ids")
    int deleteAllByIdIn(@Param("ids") Collection<UUID> ids);

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

    // Kho "Của tôi" (US-40, BR-15): khoảnh khắc lưu giữ đã xử lý xong của tôi, không giới hạn 24 giờ.
    // Dùng chỉ mục một phần moments_owner_kept_idx (DB mục 3).
    String MINE_ARCHIVE_CONDITIONS = "m.owner_id = :me AND m.mode = 'KEPT' AND m.status = 'READY'";

    @Query(value = "SELECT m.* FROM moments m WHERE " + MINE_ARCHIVE_CONDITIONS
            + " ORDER BY m.created_at DESC, m.id DESC LIMIT :limit", nativeQuery = true)
    List<Moment> mineArchiveFirstPage(@Param("me") UUID me, @Param("limit") int limit);

    @Query(value = "SELECT m.* FROM moments m WHERE " + MINE_ARCHIVE_CONDITIONS
            + " AND (m.created_at, m.id) < (:cursorCreatedAt, :cursorId)"
            + " ORDER BY m.created_at DESC, m.id DESC LIMIT :limit", nativeQuery = true)
    List<Moment> mineArchivePageAfter(@Param("me") UUID me, @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId, @Param("limit") int limit);

    // Kho "Được gửi" (US-41, BR-19, DB mục 7): khoảnh khắc lưu giữ gửi cho tôi từ người đang là bạn.
    // Như feed, danh sách bạn lấy qua FriendService thay cho EXISTS trên friendships (D-035); :friendIds không rỗng.
    // Sắp xếp theo r.created_at (chép từ moments lúc đăng) để dùng chỉ mục moment_recipients_feed_idx.
    String RECEIVED_ARCHIVE_FROM = """
            moment_recipients r JOIN moments m ON m.id = r.moment_id
            WHERE r.recipient_id = :me AND m.mode = 'KEPT' AND m.status = 'READY' AND m.owner_id IN (:friendIds)
            """;

    @Query(value = "SELECT m.* FROM " + RECEIVED_ARCHIVE_FROM
            + " ORDER BY r.created_at DESC, r.moment_id DESC LIMIT :limit", nativeQuery = true)
    List<Moment> receivedArchiveFirstPage(@Param("me") UUID me, @Param("friendIds") Collection<UUID> friendIds,
            @Param("limit") int limit);

    @Query(value = "SELECT m.* FROM " + RECEIVED_ARCHIVE_FROM
            + " AND (r.created_at, r.moment_id) < (:cursorCreatedAt, :cursorId)"
            + " ORDER BY r.created_at DESC, r.moment_id DESC LIMIT :limit", nativeQuery = true)
    List<Moment> receivedArchivePageAfter(@Param("me") UUID me, @Param("friendIds") Collection<UUID> friendIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt, @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);
}
