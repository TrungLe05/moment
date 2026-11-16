package com.dev.moment.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Ảnh chụp thông tin tài khoản để các gói khác (auth, friend...) dùng mà không chạm vào entity.
 * Chứa password hash nên không bao giờ trả thẳng ra API; ra API dùng {@link UserResponse}.
 */
public record UserAccount(
        UUID id,
        String email,
        Instant emailVerifiedAt,
        String passwordHash,
        String username,
        String displayName,
        String avatarKey,
        String locale,
        boolean demo) {

    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean needsOnboarding() {
        return username == null;
    }

    @Override
    public String toString() {
        // Không in email, password hash khi record bị log nhầm.
        return "UserAccount[id=" + id + "]";
    }
}
