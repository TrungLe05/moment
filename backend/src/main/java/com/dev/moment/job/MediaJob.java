package com.dev.moment.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng media_jobs: hàng đợi việc nền bằng PostgreSQL (ADR 0003). */
@Entity
@Table(name = "media_jobs")
class MediaJob {

    enum Status { PENDING, RUNNING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaJobType type;

    @Column(name = "moment_id")
    private UUID momentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "run_after", nullable = false)
    private Instant runAfter;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaJob() {
    }

    MediaJob(MediaJobType type, UUID momentId, String payload, Instant now) {
        this.type = type;
        this.momentId = momentId;
        this.payload = payload;
        this.runAfter = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    Long getId() {
        return id;
    }

    MediaJobType getType() {
        return type;
    }

    UUID getMomentId() {
        return momentId;
    }

    String getPayload() {
        return payload;
    }

    short getAttempts() {
        return attempts;
    }
}
