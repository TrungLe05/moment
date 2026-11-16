package com.dev.moment.reaction;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Bảng reactions: tối đa một reaction mỗi người trên một khoảnh khắc (BR-21). Ghi qua câu upsert ở repository. */
@Entity
@Table(name = "reactions")
class Reaction {

    @Embeddable
    record Key(@Column(name = "moment_id") UUID momentId, @Column(name = "user_id") UUID userId) {
    }

    @EmbeddedId
    private Key key;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Lần thả hoặc đổi emoji gần nhất; API trả là reactedAt.
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reaction() {
    }

    UUID getMomentId() {
        return key.momentId();
    }

    UUID getUserId() {
        return key.userId();
    }

    String getEmoji() {
        return emoji;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
