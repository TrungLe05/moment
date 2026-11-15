package com.dev.moment.common.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi của API, khớp bảng mã lỗi ở danh-sach-api.md mục 7.
 * Thêm mã mới phải thêm vào bảng trong tài liệu trước.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    GOOGLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    TOKEN_REUSED(HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    PROFILE_INCOMPLETE(HttpStatus.FORBIDDEN),
    REAUTH_REQUIRED(HttpStatus.FORBIDDEN),
    DEMO_ACCOUNT_READONLY(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    INVITE_INVALID(HttpStatus.NOT_FOUND),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    USERNAME_TAKEN(HttpStatus.CONFLICT),
    UPLOAD_INCOMPLETE(HttpStatus.CONFLICT),
    MOMENT_NOT_DRAFT(HttpStatus.CONFLICT),
    MOMENT_NOT_FAILED(HttpStatus.CONFLICT),
    MOMENT_NOT_KEEPABLE(HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INVITE_SELF(HttpStatus.UNPROCESSABLE_CONTENT),
    FRIEND_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_CONTENT),
    NO_FRIENDS(HttpStatus.UNPROCESSABLE_CONTENT),
    NOT_FRIENDS(HttpStatus.UNPROCESSABLE_CONTENT),
    REACTION_OWN_MOMENT(HttpStatus.UNPROCESSABLE_CONTENT),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private static final String TYPE_BASE = "https://moment.app/errors/";

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** URI của trường {@code type} trong Problem Details, ví dụ .../errors/validation-failed. */
    public String typeUri() {
        return TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
