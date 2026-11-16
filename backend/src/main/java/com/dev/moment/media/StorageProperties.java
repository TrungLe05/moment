package com.dev.moment.media;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Object storage chuẩn S3: RustFS khi dev (D-010), Cloudflare R2 khi triển khai. Đọc từ biến S3_* (.env.example).
 *
 * @param endpoint địa chỉ S3 mà cả server và trình duyệt gọi được (presigned URL dùng chính địa chỉ này)
 */
@Validated
@ConfigurationProperties("moment.storage")
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey) {
}
