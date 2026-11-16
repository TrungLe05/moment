package com.dev.moment.common.error;

import com.dev.moment.common.web.RequestIdFilter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Dựng {@link ProblemResponse} với title, detail, message theo ngôn ngữ của request (Accept-Language).
 * Dùng chung cho controller advice và các handler của Spring Security để mọi lỗi cùng một định dạng.
 */
@Component
public class ProblemFactory {

    private final MessageSource messageSource;

    public ProblemFactory(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ProblemResponse create(ErrorCode code, Object[] detailArgs, List<FieldViolation> violations) {
        Locale locale = LocaleContextHolder.getLocale();
        String title = messageSource.getMessage("error." + code.name() + ".title", null, code.name(), locale);
        String detail = messageSource.getMessage("error." + code.name() + ".detail", detailArgs, null, locale);
        List<ProblemResponse.FieldErrorItem> errors = violations.isEmpty()
                ? null
                : violations.stream()
                        .map(violation -> new ProblemResponse.FieldErrorItem(
                                violation.field(), violation.code(), fieldMessage(violation, locale)))
                        .toList();
        return new ProblemResponse(code.typeUri(), title, code.status().value(), detail, code.name(), errors,
                MDC.get(RequestIdFilter.MDC_KEY));
    }

    public ProblemResponse create(ErrorCode code) {
        return create(code, new Object[0], List.of());
    }

    // Thông báo dùng tham số có tên ({max}, {min}) để người viết bản dịch đọc dễ hơn chỉ số {0}.
    private String fieldMessage(FieldViolation violation, Locale locale) {
        String template = messageSource.getMessage("validation." + violation.code(), null, violation.code(), locale);
        String message = template;
        for (Map.Entry<String, Object> arg : violation.args().entrySet()) {
            message = message.replace("{" + arg.getKey() + "}", String.valueOf(arg.getValue()));
        }
        return message;
    }
}
