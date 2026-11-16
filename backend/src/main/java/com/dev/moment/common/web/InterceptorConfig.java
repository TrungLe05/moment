package com.dev.moment.common.web;

import com.dev.moment.common.ratelimit.DefaultRateLimitInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Đăng ký interceptor cho /api/v1/**. Interceptor chạy trong DispatcherServlet nên lỗi của nó
 * đi qua GlobalExceptionHandler như mọi lỗi khác.
 */
@Configuration(proxyBeanMethods = false)
class InterceptorConfig implements WebMvcConfigurer {

    // Nhóm Auth và hồ sơ được gọi khi chưa có username (danh-sach-api.md mục 1).
    private static final List<String> ONBOARDING_PATHS = List.of(
            "/api/v1/auth/**", "/api/v1/me", "/api/v1/me/**", "/api/v1/usernames/**");

    private final DefaultRateLimitInterceptor defaultRateLimitInterceptor;
    private final HandlerInterceptor profileCompletionInterceptor;

    InterceptorConfig(DefaultRateLimitInterceptor defaultRateLimitInterceptor,
            @Qualifier("profileCompletionInterceptor") HandlerInterceptor profileCompletionInterceptor) {
        this.defaultRateLimitInterceptor = defaultRateLimitInterceptor;
        this.profileCompletionInterceptor = profileCompletionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(defaultRateLimitInterceptor).addPathPatterns("/api/v1/**");
        registry.addInterceptor(profileCompletionInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(ONBOARDING_PATHS);
    }
}
