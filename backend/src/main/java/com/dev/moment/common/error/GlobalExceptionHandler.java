package com.dev.moment.common.error;

import jakarta.validation.ConstraintViolation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Nơi duy nhất chuyển exception thành Problem Details (CLAUDE.md, quy ước backend).
 * Không bao giờ trả stack trace hay thông điệp nội bộ cho client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    private final ProblemFactory problemFactory;

    public GlobalExceptionHandler(ProblemFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemResponse> handleApi(ApiException exception) {
        HttpHeaders headers = new HttpHeaders();
        exception.headers().forEach(headers::add);
        return respond(exception.code(), exception.detailArgs(), exception.violations(), headers);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemResponse> handleBodyValidation(MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        return validationFailed(violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemResponse> handleParameterValidation(HandlerMethodValidationException exception) {
        List<FieldViolation> violations = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    violations.add(FieldViolation.of(name, constraintCode(error.getCodes()))));
        });
        return validationFailed(violations);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ProblemResponse> handleMalformedRequest(Exception exception) {
        return validationFailed(List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ProblemResponse> handleNoRoute(Exception exception) {
        return respond(ErrorCode.NOT_FOUND, new Object[0], List.of(), new HttpHeaders());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemResponse> handleMediaType(HttpMediaTypeNotSupportedException exception) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, new Object[0], List.of(), new HttpHeaders());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemResponse> handleUnexpected(Exception exception) {
        // Log đầy đủ ở server để tra theo requestId; client chỉ nhận INTERNAL_ERROR.
        log.error("Lỗi không mong đợi", exception);
        return respond(ErrorCode.INTERNAL_ERROR, new Object[0], List.of(), new HttpHeaders());
    }

    private ResponseEntity<ProblemResponse> validationFailed(List<FieldViolation> violations) {
        return respond(ErrorCode.VALIDATION_FAILED, new Object[] {violations.size()}, violations, new HttpHeaders());
    }

    private ResponseEntity<ProblemResponse> respond(ErrorCode code, Object[] detailArgs,
            List<FieldViolation> violations, HttpHeaders headers) {
        return ResponseEntity.status(code.status())
                .headers(headers)
                .contentType(PROBLEM_JSON)
                .body(problemFactory.create(code, detailArgs, violations));
    }

    private static FieldViolation toViolation(FieldError error) {
        Map<String, Object> args = new HashMap<>();
        String code = constraintCode(error.getCodes());
        if (error.contains(ConstraintViolation.class)) {
            Map<String, Object> attributes = error.unwrap(ConstraintViolation.class)
                    .getConstraintDescriptor().getAttributes();
            for (String key : List.of("min", "max")) {
                if (attributes.containsKey(key)) {
                    args.put(key, attributes.get(key));
                }
            }
            if ("Size".equals(error.getCode())) {
                code = sizeCode(error.getRejectedValue(), attributes);
            }
        }
        return new FieldViolation(error.getField(), code, args);
    }

    // Size dùng chung cho quá ngắn và quá dài nên phải nhìn giá trị bị từ chối để chọn mã.
    private static String sizeCode(Object rejectedValue, Map<String, Object> attributes) {
        if (rejectedValue instanceof CharSequence text && attributes.get("min") instanceof Integer min
                && text.length() < min) {
            return "TOO_SHORT";
        }
        return "TOO_LONG";
    }

    private static String constraintCode(String[] codes) {
        if (codes == null || codes.length == 0) {
            return "INVALID";
        }
        // Mã cuối cùng là tên annotation, ví dụ "NotBlank".
        return switch (codes[codes.length - 1]) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            case "Email", "Pattern" -> "INVALID_FORMAT";
            case "Size", "MaxGraphemes" -> "TOO_LONG";
            default -> "INVALID";
        };
    }
}
