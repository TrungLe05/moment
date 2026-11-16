package com.dev.moment.user;

import com.dev.moment.common.error.ApiException;
import com.dev.moment.common.error.ErrorCode;
import com.dev.moment.common.ratelimit.RateLimit;
import com.dev.moment.common.ratelimit.RateLimiter;
import com.dev.moment.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Hồ sơ của tôi (danh-sach-api.md mục 3.2). Ảnh đại diện và xóa tài khoản làm ở T-116, T-118. */
@RestController
@RequestMapping("/api/v1")
class UserController {

    private final UserService userService;
    private final UserResponseFactory userResponseFactory;
    private final RateLimiter rateLimiter;

    UserController(UserService userService, UserResponseFactory userResponseFactory, RateLimiter rateLimiter) {
        this.userService = userService;
        this.userResponseFactory = userResponseFactory;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userResponseFactory.toResponse(requireAccount(CurrentUser.id(jwt)));
    }

    @PatchMapping("/me")
    UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return userResponseFactory.toResponse(userService.updateProfile(CurrentUser.id(jwt), request));
    }

    @GetMapping("/usernames/{username}/availability")
    AvailabilityResponse availability(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        // Giới hạn riêng để chống dò danh sách username.
        rateLimiter.consume(RateLimit.USERNAME_CHECK_PER_USER, jwt.getSubject());
        return new AvailabilityResponse(userService.isUsernameAvailable(username));
    }

    private UserAccount requireAccount(UUID userId) {
        // Token còn hạn nhưng tài khoản đã bị xóa thì coi như chưa đăng nhập.
        return userService.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    record AvailabilityResponse(boolean available) {
    }
}
