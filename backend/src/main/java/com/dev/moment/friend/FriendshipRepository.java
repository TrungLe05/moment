package com.dev.moment.friend;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FriendshipRepository extends JpaRepository<Friendship, Friendship.FriendshipId> {

    @Query("select count(f) from Friendship f where f.id.userAId = :userId or f.id.userBId = :userId")
    long countByUser(@Param("userId") UUID userId);

    // Tối đa 15 dòng (BR-06) nên không cần phân trang.
    @Query("""
            select f from Friendship f where f.id.userAId = :userId or f.id.userBId = :userId
            order by f.createdAt desc
            """)
    List<Friendship> findAllByUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from Friendship f where f.id.userAId = :first and f.id.userBId = :second")
    int deletePair(@Param("first") UUID first, @Param("second") UUID second);
}
