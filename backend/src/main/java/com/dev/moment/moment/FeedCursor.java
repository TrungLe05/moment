package com.dev.moment.moment;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.common.error.FieldViolation;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Con trỏ phân trang (created_at, id) của khoảnh khắc cuối trang trước (DB mục 7). Gửi cho client dạng chuỗi mờ
 * base64url; client không tự tạo (API mục 1), chuỗi không đọc được thì báo VALIDATION_FAILED.
 */
record FeedCursor(Instant createdAt, UUID id) {

    private static final String SEPARATOR = "~";

    static FeedCursor after(Moment moment) {
        return new FeedCursor(moment.getCreatedAt(), moment.getId());
    }

    String encode() {
        String raw = createdAt + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static FeedCursor decode(String text) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator < 0) {
                throw invalid();
            }
            return new FeedCursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException malformed) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return ApiException.withViolations(ErrorCode.VALIDATION_FAILED,
                List.of(FieldViolation.of("cursor", "INVALID_FORMAT")));
    }
}
