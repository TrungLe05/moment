package com.dev.moment.media;

import java.util.Map;
import java.util.Optional;

/**
 * Định dạng và dung lượng file tải lên (BR-10). Server không tin phần mở rộng hay tên file client gửi:
 * đuôi trong khóa file suy ra từ Content-Type đã kiểm tra (CLAUDE.md, file tải lên).
 */
public final class MediaRules {

    public static final long MAX_PHOTO_BYTES = 15L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;

    private static final Map<String, String> PHOTO_TYPES = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", "mp4", "video/webm", "webm", "video/quicktime", "mov");
    // Bản ghép xuất từ canvas chỉ là JPEG hoặc WebP (API mục 3.4).
    private static final Map<String, String> COMPOSED_TYPES = Map.of("image/jpeg", "jpg", "image/webp", "webp");

    private MediaRules() {
    }

    /** Đuôi file cho ảnh gốc; rỗng nếu định dạng không hỗ trợ. */
    public static Optional<String> photoExtension(String contentType) {
        return Optional.ofNullable(PHOTO_TYPES.get(contentType));
    }

    public static Optional<String> videoExtension(String contentType) {
        return Optional.ofNullable(VIDEO_TYPES.get(contentType));
    }

    public static Optional<String> composedExtension(String contentType) {
        return Optional.ofNullable(COMPOSED_TYPES.get(contentType));
    }
}
