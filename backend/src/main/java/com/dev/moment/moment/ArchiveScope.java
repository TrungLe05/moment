package com.dev.moment.moment;

/** Hai tab của kho (US-40, US-41): giá trị query `scope` của `GET /archive` (API mục 3.4). */
enum ArchiveScope {
    /** "Của tôi": khoảnh khắc lưu giữ tôi đăng. */
    MINE,
    /** "Được gửi": khoảnh khắc lưu giữ bạn bè gửi cho tôi. */
    RECEIVED;

    /** Chuỗi đã qua kiểm tra `mine|received` ở controller. */
    static ArchiveScope of(String value) {
        return "received".equals(value) ? RECEIVED : MINE;
    }
}
