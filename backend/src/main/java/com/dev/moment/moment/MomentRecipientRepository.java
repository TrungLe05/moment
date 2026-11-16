package com.dev.moment.moment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MomentRecipientRepository extends JpaRepository<MomentRecipient, MomentRecipient.Key> {

    @Query("select count(r) from MomentRecipient r where r.key.momentId = :momentId")
    long countByMomentId(@Param("momentId") UUID momentId);

    /** Số người nhận của nhiều khoảnh khắc trong một truy vấn: mỗi phần tử là [momentId, count]. */
    @Query("""
            select r.key.momentId, count(r) from MomentRecipient r
            where r.key.momentId in :momentIds group by r.key.momentId
            """)
    List<Object[]> countByMomentIds(@Param("momentIds") Collection<UUID> momentIds);
}
