package com.dev.moment.moment;

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

/** Bảng moment_media: mỗi biến thể file của một khoảnh khắc (thiết kế database mục 6). */
@Entity
@Table(name = "moment_media")
class MomentMedia {

    enum Variant { ORIGINAL, COMPOSED, DISPLAY, VIDEO, THUMBNAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "moment_id", nullable = false)
    private UUID momentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Variant variant;

    @Column(name = "object_key", nullable = false, length = 300)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MomentMedia() {
    }

    MomentMedia(UUID momentId, Variant variant, String objectKey, String contentType, long sizeBytes, Instant now) {
        this.momentId = momentId;
        this.variant = variant;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = now;
    }

    Variant getVariant() {
        return variant;
    }

    String getObjectKey() {
        return objectKey;
    }

    String getContentType() {
        return contentType;
    }

    Long getSizeBytes() {
        return sizeBytes;
    }
}
