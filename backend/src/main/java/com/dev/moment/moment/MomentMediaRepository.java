package com.dev.moment.moment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MomentMediaRepository extends JpaRepository<MomentMedia, UUID> {

    List<MomentMedia> findAllByMomentId(UUID momentId);
}
