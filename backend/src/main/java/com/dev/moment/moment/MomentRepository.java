package com.dev.moment.moment;

import java.time.Instant;
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
}
