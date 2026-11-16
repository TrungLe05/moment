package com.dev.moment.user;

import java.util.UUID;

/** "User tóm tắt" dùng ở trang mời, danh sách bạn bè, chủ khoảnh khắc (danh-sach-api.md mục 3.3, 4). */
public record UserSummary(UUID id, String username, String displayName, String avatarUrl) {
}
