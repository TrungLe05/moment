package com.dev.moment.common.error;

import java.util.List;
import java.util.Map;

/**
 * Exception nghiệp vụ mang mã lỗi; {@link GlobalExceptionHandler} chuyển thành Problem Details.
 * {@code detailArgs} điền vào chuỗi detail trong messages*.properties, {@code headers} thêm vào phản hồi
 * (ví dụ {@code Retry-After} cho LOGIN_LOCKED).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object[] detailArgs;
    private final transient List<FieldViolation> violations;
    private final transient Map<String, String> headers;

    public ApiException(ErrorCode code) {
        this(code, new Object[0], List.of(), Map.of());
    }

    public ApiException(ErrorCode code, Object[] detailArgs, List<FieldViolation> violations, Map<String, String> headers) {
        super(code.name());
        this.code = code;
        this.detailArgs = detailArgs.clone();
        this.violations = List.copyOf(violations);
        this.headers = Map.copyOf(headers);
    }

    public static ApiException withArgs(ErrorCode code, Object... detailArgs) {
        return new ApiException(code, detailArgs, List.of(), Map.of());
    }

    public static ApiException withViolations(ErrorCode code, List<FieldViolation> violations) {
        return new ApiException(code, new Object[0], violations, Map.of());
    }

    public static ApiException withHeaders(ErrorCode code, Map<String, String> headers) {
        return new ApiException(code, new Object[0], List.of(), headers);
    }

    public ErrorCode code() {
        return code;
    }

    public Object[] detailArgs() {
        return detailArgs.clone();
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    public Map<String, String> headers() {
        return headers;
    }
}
