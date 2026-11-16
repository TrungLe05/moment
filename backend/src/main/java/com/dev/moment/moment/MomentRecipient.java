package com.dev.moment.moment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Bảng moment_recipients: người nhận chốt khi đăng (BR-12). */
@Entity
@Table(name = "moment_recipients")
class MomentRecipient {

    @EmbeddedId
    private Key key;

    // Chép từ moments.created_at để sắp xếp feed theo index của bảng này.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MomentRecipient() {
    }

    MomentRecipient(UUID momentId, UUID recipientId, Instant createdAt) {
        this.key = new Key(momentId, recipientId);
        this.createdAt = createdAt;
    }

    @Embeddable
    record Key(
            @Column(name = "moment_id") UUID momentId,
            @Column(name = "recipient_id") UUID recipientId) implements Serializable {
    }
}
