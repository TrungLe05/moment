package com.dev.moment.friend;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Bảng friendships: mỗi cặp một dòng, user_a_id < user_b_id (theo thứ tự uuid của PostgreSQL). */
@Entity
@Table(name = "friendships")
class Friendship {

    @EmbeddedId
    private FriendshipId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Friendship() {
    }

    Friendship(UserPair pair, Instant now) {
        this.id = new FriendshipId(pair.first(), pair.second());
        this.createdAt = now;
    }

    UUID otherThan(UUID userId) {
        return id.userAId().equals(userId) ? id.userBId() : id.userAId();
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    record FriendshipId(
            @Column(name = "user_a_id") UUID userAId,
            @Column(name = "user_b_id") UUID userBId) implements Serializable {
    }
}
