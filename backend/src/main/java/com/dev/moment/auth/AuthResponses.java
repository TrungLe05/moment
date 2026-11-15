package com.dev.moment.auth;

import com.dev.moment.user.UserResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Phản hồi nhóm Auth. refreshToken chỉ có với client native (danh-sach-api.md mục 2). */
final class AuthResponses {

    private AuthResponses() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Login(String accessToken, long expiresIn, String refreshToken, UserResponse user, Boolean isNewUser) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Token(String accessToken, long expiresIn, String refreshToken) {
    }
}
