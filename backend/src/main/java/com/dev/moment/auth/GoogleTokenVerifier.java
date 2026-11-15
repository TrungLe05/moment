package com.dev.moment.auth;

import java.util.Optional;

/** Xác minh Google ID token client gửi lên (US-04). Tách interface để test không phải gọi Google. */
interface GoogleTokenVerifier {

    /** Rỗng nếu token sai chữ ký, sai aud/iss, hết hạn hoặc chưa cấu hình client id. */
    Optional<GoogleIdentity> verify(String idToken);

    record GoogleIdentity(String subject, String email, boolean emailVerified) {
    }
}
