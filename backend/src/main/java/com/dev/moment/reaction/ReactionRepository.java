package com.dev.moment.reaction;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReactionRepository extends JpaRepository<Reaction, Reaction.Key> {

    // BR-21: thả mới hoặc đổi emoji trong một câu lệnh, hai request cùng lúc không tạo hai dòng.
    @Modifying
    @Query(value = """
            INSERT INTO reactions (moment_id, user_id, emoji, created_at, updated_at)
            VALUES (:momentId, :userId, :emoji, :now, :now)
            ON CONFLICT (moment_id, user_id) DO UPDATE SET emoji = EXCLUDED.emoji, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(@Param("momentId") UUID momentId, @Param("userId") UUID userId, @Param("emoji") String emoji,
            @Param("now") Instant now);

    @Modifying
    @Query("delete from Reaction r where r.key.momentId = :momentId and r.key.userId = :userId")
    int deleteMine(@Param("momentId") UUID momentId, @Param("userId") UUID userId);

    @Query("select r from Reaction r where r.key.momentId = :momentId order by r.updatedAt desc, r.key.userId")
    List<Reaction> findAllForMoment(@Param("momentId") UUID momentId);

    @Query("select r from Reaction r where r.key.userId = :userId and r.key.momentId in :momentIds")
    List<Reaction> findMine(@Param("userId") UUID userId, @Param("momentIds") Collection<UUID> momentIds);

    /** Số reaction của nhiều khoảnh khắc trong một truy vấn: mỗi phần tử là [momentId, count]. */
    @Query("""
            select r.key.momentId, count(r) from Reaction r
            where r.key.momentId in :momentIds group by r.key.momentId
            """)
    List<Object[]> countByMomentIds(@Param("momentIds") Collection<UUID> momentIds);
}
