package com.dev.moment.moment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MomentRecipientRepository extends JpaRepository<MomentRecipient, MomentRecipient.Key> {

    @Query("select count(r) from MomentRecipient r where r.key.momentId = :momentId")
    long countByMomentId(@Param("momentId") UUID momentId);
}
