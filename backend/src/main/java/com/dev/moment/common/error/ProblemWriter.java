package com.dev.moment.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ghi Problem Details thẳng ra response cho các lỗi xảy ra trong filter (Spring Security, giới hạn tần suất),
 * tức là trước DispatcherServlet nên @RestControllerAdvice không bắt được.
 */
@Component
public class ProblemWriter {

    private final ProblemFactory problemFactory;
    private final LocaleResolver localeResolver;
    private final JsonMapper jsonMapper;

    public ProblemWriter(ProblemFactory problemFactory, LocaleResolver localeResolver, JsonMapper jsonMapper) {
        this.problemFactory = problemFactory;
        this.localeResolver = localeResolver;
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        // Filter chạy trước DispatcherServlet nên phải tự xác định ngôn ngữ từ Accept-Language.
        Locale previous = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(localeResolver.resolveLocale(request));
        try {
            ProblemResponse body = problemFactory.create(code, new Object[0], List.of());
            response.setStatus(code.status().value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            jsonMapper.writeValue(response.getOutputStream(), body);
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }
}
