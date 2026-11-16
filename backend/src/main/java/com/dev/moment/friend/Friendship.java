package com.dev.moment.friend;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Bảng friendships: mỗi cặp một dòng, user_a_id < user_b_id. Kết bạn, hủy kết bạn làm ở E2 (T-122, T-123). */
@Entity
@Table(name = "friendships")
class Friendship {

    @EmbeddedId
    private FriendshipId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Friendship() {
    }

    @Embeddable
    record FriendshipId(
            @Column(name = "user_a_id") UUID userAId,
            @Column(name = "user_b_id") UUID userBId) implements Serializable {
    }
}
