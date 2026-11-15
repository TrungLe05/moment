package com.dev.moment.auth;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.common.error.FieldViolation;
import com.dev.moment.common.text.GraphemeCounter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * BR-01: mật khẩu tối thiểu 8 ký tự hiển thị. Tối đa 72 byte UTF-8 vì BCrypt chỉ dùng 72 byte đầu
 * và Spring Security từ chối mật khẩu dài hơn.
 */
final class PasswordPolicy {

    static final int MIN_CHARACTERS = 8;
    static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    static void check(String field, String password) {
        if (GraphemeCounter.count(password) < MIN_CHARACTERS) {
            throw violation(field, "TOO_SHORT", Map.of("min", MIN_CHARACTERS));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw violation(field, "TOO_LONG", Map.of("max", MAX_BYTES));
        }
    }

    private static ApiException violation(String field, String code, Map<String, Object> args) {
        return ApiException.withViolations(ErrorCode.VALIDATION_FAILED, List.of(new FieldViolation(field, code, args)));
    }
}
