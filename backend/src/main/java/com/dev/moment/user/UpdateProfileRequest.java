package com.dev.moment.user;

import com.dev.moment.common.text.MaxGraphemes;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH /me: chỉ gửi trường cần đổi, trường null giữ nguyên.
 * Username được đổi về chữ thường trước khi kiểm tra định dạng (BR-02 không phân biệt hoa thường).
 */
public record UpdateProfileRequest(
        String username,
        @MaxGraphemes(max = 40) String displayName,
        @Pattern(regexp = "vi|en") String locale) {
}
