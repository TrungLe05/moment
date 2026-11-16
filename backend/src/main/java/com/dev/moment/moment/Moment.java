package com.dev.moment.moment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng moments. Vòng đời ở thiết kế database mục 4. */
@Entity
@Table(name = "moments")
class Moment {

    enum MediaType { PHOTO, VIDEO }

    enum Source { CAMERA, UPLOAD }

    enum Mode { SHARED, KEPT }

    enum Audience { ALL, SELECTED }

    enum Status { DRAFT, PROCESSING, READY, FAILED, EXPIRED }

    // Id sinh ở ứng dụng để dựng khóa file moments/{id}/... trước khi lưu.
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 8)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Audience audience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column
    private String caption;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String layers;

    @Column(name = "layers_version", nullable = false)
    private short layersVersion;

    @Column(name = "trim_start_ms")
    private Integer trimStartMs;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "kept_at")
    private Instant keptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Moment() {
    }

    Moment(UUID id, UUID ownerId, MediaType mediaType, Source source, Mode mode, Audience audience, String caption,
            String layers, short layersVersion, Integer trimStartMs, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.mediaType = mediaType;
        this.source = source;
        this.mode = mode;
        this.audience = audience;
        this.status = Status.DRAFT;
        this.caption = caption;
        this.layers = layers;
        this.layersVersion = layersVersion;
        this.trimStartMs = trimStartMs;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        // Ràng buộc moments_kept: chế độ lưu giữ luôn có kept_at.
        this.keptAt = mode == Mode.KEPT ? createdAt : null;
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    MediaType getMediaType() {
        return mediaType;
    }

    Source getSource() {
        return source;
    }

    Mode getMode() {
        return mode;
    }

    Audience getAudience() {
        return audience;
    }

    Status getStatus() {
        return status;
    }

    String getCaption() {
        return caption;
    }

    String getLayers() {
        return layers;
    }

    short getLayersVersion() {
        return layersVersion;
    }

    Integer getTrimStartMs() {
        return trimStartMs;
    }

    Integer getDurationMs() {
        return durationMs;
    }

    Integer getWidth() {
        return width;
    }

    Integer getHeight() {
        return height;
    }

    String getFailureReason() {
        return failureReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getKeptAt() {
        return keptAt;
    }
}
