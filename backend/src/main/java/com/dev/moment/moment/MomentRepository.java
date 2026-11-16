package com.dev.moment.moment;

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
}
