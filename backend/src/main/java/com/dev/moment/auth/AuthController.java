package com.dev.moment.auth;

import com.dev.moment.common.ratelimit.RateLimit;
import com.dev.moment.common.ratelimit.RateLimiter;
import com.dev.moment.user.UserResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nhóm Auth (danh-sach-api.md mục 3.1). Refresh token: web nhận cookie HttpOnly, native nhận trong body,
 * theo header X-Client-Type (mục 2). Thiếu header thì coi là web.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String REFRESH_COOKIE = "moment_rt";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String DEFAULT_LOCALE = "vi";

    private final AuthService authService;
    private final UserResponseFactory userResponseFactory;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;

    AuthController(AuthService authService, UserResponseFactory userResponseFactory, RateLimiter rateLimiter,
            AuthProperties properties) {
        this.authService = authService;
        this.userResponseFactory = userResponseFactory;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody AuthRequests.Register request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.REGISTER_PER_IP, http.getRemoteAddr());
        authService.register(request.email(), request.password(), localeOrDefault(request.locale()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody AuthRequests.VerifyEmail request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    ResponseEntity<Void> resendVerification(@Valid @RequestBody AuthRequests.EmailOnly request) {
        rateLimiter.consume(RateLimit.EMAIL_REQUEST_PER_EMAIL, "verify:" + normalizeEmail(request.email()));
        authService.resendVerification(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponses.Login> login(@Valid @RequestBody AuthRequests.Login request,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.LOGIN_PER_IP, http.getRemoteAddr());
        AuthService.AuthSession session = authService.login(request.email(), request.password(), userAgent(http));
        return sessionResponse(session, isNative(clientType));
    }

    @PostMapping("/google")
    ResponseEntity<AuthResponses.Login> google(@Valid @RequestBody AuthRequests.Google request,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.LOGIN_PER_IP, http.getRemoteAddr());
        AuthService.AuthSession session = authService.loginWithGoogle(request.idToken(),
                localeOrDefault(request.locale()), userAgent(http));
        return sessionResponse(session, isNative(clientType));
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponses.Token> refresh(@RequestBody(required = false) AuthRequests.Refresh body,
            @CookieValue(value = REFRESH_COOKIE, required = false) String cookieToken,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType, HttpServletRequest http) {
        boolean nativeClient = isNative(clientType);
        String presented = nativeClient ? (body == null ? null : body.refreshToken()) : cookieToken;
        AuthService.RefreshedSession session = authService.refresh(presented, userAgent(http));
        AuthResponses.Token response = new AuthResponses.Token(session.accessToken().token(),
                session.accessToken().expiresInSeconds(), nativeClient ? session.refreshToken() : null);
        return withRefreshCookie(ResponseEntity.ok(), nativeClient, session.refreshToken()).body(response);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestBody(required = false) AuthRequests.Refresh body,
            @CookieValue(value = REFRESH_COOKIE, required = false) String cookieToken,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType) {
        boolean nativeClient = isNative(clientType);
        authService.logout(nativeClient ? (body == null ? null : body.refreshToken()) : cookieToken);
        ResponseEntity.HeadersBuilder<?> builder = ResponseEntity.noContent();
        if (!nativeClient) {
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        }
        return builder.build();
    }

    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody AuthRequests.EmailOnly request) {
        rateLimiter.consume(RateLimit.EMAIL_REQUEST_PER_EMAIL, "reset:" + normalizeEmail(request.email()));
        authService.forgotPassword(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthRequests.ResetPassword request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponses.Login> sessionResponse(AuthService.AuthSession session, boolean nativeClient) {
        AuthResponses.Login response = new AuthResponses.Login(session.accessToken().token(),
                session.accessToken().expiresInSeconds(), nativeClient ? session.refreshToken() : null,
                userResponseFactory.toResponse(session.account()), session.newUser());
        return withRefreshCookie(ResponseEntity.ok(), nativeClient, session.refreshToken()).body(response);
    }

    private ResponseEntity.BodyBuilder withRefreshCookie(ResponseEntity.BodyBuilder builder, boolean nativeClient,
            String refreshToken) {
        if (!nativeClient) {
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, properties.refreshTokenTtl()).toString());
        }
        return builder;
    }

    // Cookie chỉ gửi kèm các request tới /api/v1/auth, JavaScript không đọc được (danh-sach-api.md mục 2).
    static ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private static boolean isNative(String clientType) {
        return "native".equalsIgnoreCase(clientType);
    }

    private static String localeOrDefault(String locale) {
        return locale == null ? DEFAULT_LOCALE : locale;
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
