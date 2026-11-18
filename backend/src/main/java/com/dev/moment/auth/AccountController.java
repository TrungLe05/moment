package com.dev.moment.auth;

import com.dev.moment.common.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** `DELETE /me` (danh-sach-api.md mục 3.2): đặt ở gói auth vì cần kiểm mật khẩu và auth_time (D-026). */
@RestController
@RequestMapping("/api/v1")
class AccountController {

    private final AccountDeletionService accountDeletionService;

    AccountController(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    @DeleteMapping("/me")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) DeleteAccount body,
            @RequestHeader(value = "X-Client-Type", required = false) String clientType) {
        Number authTime = jwt.getClaim(AccessTokenService.AUTH_TIME_CLAIM);
        accountDeletionService.delete(CurrentUser.id(jwt), body == null ? null : body.password(),
                authTime == null ? null : Instant.ofEpochSecond(authTime.longValue()));
        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        // Bản web giữ refresh token trong cookie (API mục 2): tài khoản đã xóa thì xóa luôn cookie.
        if (!"native".equalsIgnoreCase(clientType)) {
            response.header(HttpHeaders.SET_COOKIE, AuthController.refreshCookie("", Duration.ZERO).toString());
        }
        return response.build();
    }

    record DeleteAccount(String password) {
    }
}
