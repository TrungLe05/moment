package com.dev.moment.user;

import java.util.UUID;

/** Đối tượng user trả về ở /me và phản hồi đăng nhập (danh-sach-api.md mục 2). */
public record UserResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String avatarUrl,
        String locale,
        boolean needsOnboarding,
        int friendCount,
        int friendLimit,
        boolean isDemo) {
}
