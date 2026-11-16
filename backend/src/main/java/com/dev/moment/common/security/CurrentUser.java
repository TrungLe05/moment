package com.dev.moment.common.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Lấy id người dùng từ access token đã được Spring Security xác minh. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
