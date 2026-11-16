package com.dev.moment.job;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Xử lý một loại việc nền. Tính năng sở hữu dữ liệu cài đặt interface này, worker chỉ lo lấy việc và thử lại,
 * nên gói job không đọc thẳng bảng của tính năng khác (CLAUDE.md, quy ước backend).
 */
public interface MediaJobHandler {

    MediaJobType type();

    /**
     * Chạy ngoài transaction để được gọi storage và ffmpeg (CLAUDE.md). Phải chạy lại an toàn:
     * việc có thể chạy lần nữa sau khi worker chết giữa chừng.
     * Ném {@link NonRetryableJobException} khi thử lại chắc chắn vô ích; lỗi khác được thử lại.
     */
    void handle(UUID momentId, JsonNode payload);

    /** Gọi trong transaction đánh dấu việc thất bại hẳn, để cập nhật dữ liệu liên quan cùng lúc. */
    default void onFailed(UUID momentId, String reason) {
    }
}
