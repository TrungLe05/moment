package com.dev.moment.common.error;

import java.util.Map;

/**
 * Một lỗi ở một trường. {@code code} là mã ngắn (REQUIRED, TOO_LONG...) để client xử lý;
 * {@code args} điền vào câu thông báo {@code validation.<code>} theo ngôn ngữ người dùng.
 */
public record FieldViolation(String field, String code, Map<String, Object> args) {

    public FieldViolation {
        args = Map.copyOf(args);
    }

    public static FieldViolation of(String field, String code) {
        return new FieldViolation(field, code, Map.of());
    }
}
