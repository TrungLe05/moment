package com.dev.moment.moment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MomentMediaRepository extends JpaRepository<MomentMedia, UUID> {

    List<MomentMedia> findAllByMomentId(UUID momentId);

    List<MomentMedia> findAllByMomentIdIn(Collection<UUID> momentIds);

    // BR-14: khoảnh khắc hết hạn giữ bản ghi nhưng bỏ hết file; file trên storage xóa qua việc DELETE_OBJECTS.
    @Modifying
    @Query("delete from MomentMedia f where f.momentId in :momentIds")
    int deleteAllByMomentIdIn(@Param("momentIds") Collection<UUID> momentIds);
}
