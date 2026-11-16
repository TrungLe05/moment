package com.dev.moment.friend;

import java.util.List;
import java.util.UUID;

/**
 * Cặp hai người theo thứ tự cố định, dùng cho khóa chính friendships (user_a_id < user_b_id)
 * và cho thứ tự khóa dòng khi kết bạn (BR-06).
 * PostgreSQL so uuid theo từng byte không dấu, khác UUID.compareTo của Java (so long có dấu);
 * chuỗi hex chữ thường so theo từ điển cho cùng kết quả với PostgreSQL.
 */
record UserPair(UUID first, UUID second) {

    static UserPair of(UUID left, UUID right) {
        return left.toString().compareTo(right.toString()) < 0 ? new UserPair(left, right) : new UserPair(right, left);
    }

    List<UUID> inLockOrder() {
        return List.of(first, second);
    }
}
