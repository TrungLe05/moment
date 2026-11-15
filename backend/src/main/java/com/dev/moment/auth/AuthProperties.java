package com.dev.moment.auth;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình xác thực, đọc từ biến môi trường (xem .env.example).
 *
 * @param jwtSigningKey   khóa HMAC ký access token, base64, tối thiểu 32 byte (JWT_SIGNING_KEY)
 * @param googleClientId  client id OAuth của dự án; trống thì đăng nhập Google luôn bị từ chối (GOOGLE_CLIENT_ID)
 * @param frontendBaseUrl gốc URL frontend để dựng link trong email (FRONTEND_BASE_URL)
 */
@Validated
@ConfigurationProperties("moment.auth")
public record AuthProperties(
        @NotBlank String jwtSigningKey,
        String googleClientId,
        @NotBlank String frontendBaseUrl,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration verifyEmailTtl,
        Duration resetPasswordTtl) {

    public AuthProperties {
        // Thời hạn theo đặc tả: access 15 phút, refresh 30 ngày (mục 5), link xác thực 24 giờ (US-02), đặt lại 1 giờ (US-05).
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
        verifyEmailTtl = verifyEmailTtl == null ? Duration.ofHours(24) : verifyEmailTtl;
        resetPasswordTtl = resetPasswordTtl == null ? Duration.ofHours(1) : resetPasswordTtl;
        googleClientId = googleClientId == null ? "" : googleClientId.strip();
        frontendBaseUrl = frontendBaseUrl == null ? null : frontendBaseUrl.replaceAll("/+$", "");
    }
}
