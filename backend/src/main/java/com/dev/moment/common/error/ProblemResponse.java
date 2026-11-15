package com.dev.moment.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Thân phản hồi lỗi theo RFC 9457, thêm {@code code}, {@code errors}, {@code requestId} (danh-sach-api.md mục 1).
 * Gửi với Content-Type {@code application/problem+json}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemResponse(
        String type,
        String title,
        int status,
        String detail,
        String code,
        List<FieldErrorItem> errors,
        String requestId) {

    public record FieldErrorItem(String field, String code, String message) {
    }
}
