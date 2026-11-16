package com.dev.moment.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng media_jobs: hàng đợi việc nền bằng PostgreSQL (ADR 0003). Worker lấy việc ở T-132. */
@Entity
@Table(name = "media_jobs")
class MediaJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "moment_id")
    private UUID momentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    @Column(nullable = false)
    private short attempts;

    @Column(name = "run_after", nullable = false)
    private Instant runAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaJob() {
    }

    MediaJob(String type, UUID momentId, String payload, Instant now) {
        this.type = type;
        this.momentId = momentId;
        this.payload = payload;
        this.runAfter = now;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
