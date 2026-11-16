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
import java.util.Locale;
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

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "duration_ms")
    private Integer durationMs;

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

    /** File do worker tạo, có kích thước đo bằng ffprobe. */
    MomentMedia(UUID momentId, Variant variant, String objectKey, String contentType, long sizeBytes, int width,
            int height, Integer durationMs, Instant now) {
        this(momentId, variant, objectKey, contentType, sizeBytes, now);
        this.width = width;
        this.height = height;
        this.durationMs = durationMs;
    }

    /** DB mục 6: moments/{momentId}/{variant}-{uuid}.{ext}; uuid ngẫu nhiên để không ai đoán được khóa. */
    static String objectKey(UUID momentId, Variant variant, String extension) {
        return "moments/" + momentId + "/" + variant.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "."
                + extension;
    }

    UUID getId() {
        return id;
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
