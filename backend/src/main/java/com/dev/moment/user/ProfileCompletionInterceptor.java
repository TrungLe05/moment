package com.dev.moment.user;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Người dùng chưa chọn username gọi API ngoài nhóm Auth và hồ sơ thì nhận 403 PROFILE_INCOMPLETE
 * (danh-sach-api.md mục 1, US-06). Kiểm tra từ database mỗi request nên đổi username có hiệu lực ngay.
 */
@Component
class ProfileCompletionInterceptor implements HandlerInterceptor {

    private final UserService userService;

    ProfileCompletionInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return true;
        }
        UserAccount account = userService.findById(UUID.fromString(token.getName()))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (account.needsOnboarding()) {
            throw new ApiException(ErrorCode.PROFILE_INCOMPLETE);
        }
        return true;
    }
}
