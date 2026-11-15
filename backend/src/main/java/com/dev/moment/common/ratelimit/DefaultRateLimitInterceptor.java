package com.dev.moment.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Giới hạn chung 300 yêu cầu/phút cho mỗi người dùng đã đăng nhập (danh-sach-api.md mục 6, dòng "Còn lại"). */
@Component
public class DefaultRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    public DefaultRateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            rateLimiter.consume(RateLimit.DEFAULT_PER_USER, token.getName());
        }
        return true;
    }
}
