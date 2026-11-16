package com.dev.moment.media;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Thao tác với object storage. File không đi qua API: client tải thẳng bằng presigned URL (ARCHITECTURE.md mục 1).
 * Không gọi các hàm này bên trong transaction database (CLAUDE.md).
 */
public interface ObjectStorage {

    /** URL cho client PUT file; chữ ký gồm Content-Type và dung lượng nên client không đổi được. */
    PresignedUpload presignUpload(String objectKey, String contentType, long sizeBytes, Duration ttl);

    /** URL đọc file có hạn; chỉ cấp sau khi đã kiểm tra quyền xem (BR-20). */
    PresignedDownload presignDownload(String objectKey, Duration ttl);

    /** Thông tin file nếu đã có trên storage. */
    Optional<StoredObject> find(String objectKey);

    void deleteAll(Collection<String> objectKeys);

    record PresignedUpload(String method, String url, Map<String, String> headers, Instant expiresAt) {
    }

    record PresignedDownload(String url, Instant expiresAt) {
    }

    record StoredObject(long sizeBytes, String contentType) {
    }
}
