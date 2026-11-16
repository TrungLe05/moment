package com.dev.moment.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body các request nhóm Auth (danh-sach-api.md mục 3.1). Bean Validation chỉ kiểm tra định dạng;
 * độ dài mật khẩu theo ký tự hiển thị kiểm tra ở {@link PasswordPolicy}.
 */
final class AuthRequests {

    private AuthRequests() {
    }

    record Register(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String password,
            @Pattern(regexp = "vi|en") String locale) {
    }

    record VerifyEmail(@NotBlank String token) {
    }

    record EmailOnly(@NotBlank @Email @Size(max = 254) String email) {
    }

    record Login(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
    }

    record Google(@NotBlank String idToken, @Pattern(regexp = "vi|en") String locale) {
    }

    /** Chỉ client native gửi body; web đọc refresh token từ cookie. */
    record Refresh(String refreshToken) {
    }

    record ResetPassword(@NotBlank String token, @NotBlank String newPassword) {
    }
}
