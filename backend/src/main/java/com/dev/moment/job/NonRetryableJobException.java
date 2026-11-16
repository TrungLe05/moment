package com.dev.moment.job;

/**
 * Lỗi mà thử lại cũng ra cùng kết quả (file hỏng, video quá dài). Worker đánh dấu thất bại ngay,
 * vì CLAUDE.md chỉ cho thử lại lỗi tạm thời.
 */
public class NonRetryableJobException extends RuntimeException {

    private final String reason;

    /** @param reason mã lý do lưu vào failure_reason (D-033) */
    public NonRetryableJobException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public NonRetryableJobException(String reason, String message) {
        this(reason, message, null);
    }

    public String reason() {
        return reason;
    }
}
