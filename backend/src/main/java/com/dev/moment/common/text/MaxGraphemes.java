package com.dev.moment.common.text;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Giới hạn số ký tự hiển thị (grapheme) của chuỗi; null hợp lệ, kết hợp @NotBlank nếu bắt buộc. */
@Documented
@Constraint(validatedBy = MaxGraphemesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxGraphemes {

    int max();

    String message() default "TOO_LONG";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
