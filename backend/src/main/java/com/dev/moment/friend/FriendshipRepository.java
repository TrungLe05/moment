package com.dev.moment.friend;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FriendshipRepository extends JpaRepository<Friendship, Friendship.FriendshipId> {

    @Query("select count(f) from Friendship f where f.id.userAId = :userId or f.id.userBId = :userId")
    long countByUser(@Param("userId") UUID userId);
}
