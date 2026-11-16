package com.dev.moment.auth;

import static com.dev.moment.support.ApiTestClient.PASSWORD;
import static com.dev.moment.support.ApiTestClient.randomIp;
import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class AuthFlowTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RecordingEmailSender emails;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtDecoder jwtDecoder;

    private ApiTestClient api;
    private final String wrongPassword = ApiTestClient.newPassword();
    private final String newPassword = ApiTestClient.newPassword();

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, jsonMapper, emails);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    // --- Đăng ký và xác thực email (US-01, US-02, BR-01) ---

    @Test
    void shouldRegisterAndSendVerificationLinkInUserLocale() throws Exception {
        String email = uniqueEmail();
        MockHttpServletResponse response = api.register(email, PASSWORD);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(emails.sentTo(email)).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("Xác thực email cho Khoảnh khắc");
            assertThat(mail.body()).contains("http://localhost:3000/verify-email?token=");
        });
    }

    @Test
    void shouldRejectRegistrationWhenEmailTakenIgnoringCase() throws Exception {
        String email = uniqueEmail();
        api.register(email, PASSWORD);

        MockHttpServletResponse response = api.register(email.toUpperCase(), PASSWORD);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(api.read(response).get("code").asString()).isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void shouldRejectPasswordShorterThanEightCharacters() throws Exception {
        MockHttpServletResponse response = api.register(uniqueEmail(), "ngắn7kt");

        assertThat(response.getStatus()).isEqualTo(400);
        JsonNode error = api.read(response).get("errors").get(0);
        assertThat(error.get("field").asString()).isEqualTo("password");
        assertThat(error.get("code").asString()).isEqualTo("TOO_SHORT");
    }

    @Test
    void shouldRejectLoginUntilEmailIsVerified() throws Exception {
        String email = uniqueEmail();
        api.register(email, PASSWORD);

        MockHttpServletResponse response = api.login(email, PASSWORD, null);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(api.read(response).get("code").asString()).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void shouldAcceptVerificationLinkOnlyOnce() throws Exception {
        String email = uniqueEmail();
        api.register(email, PASSWORD);
        String token = emails.latestToken(email, "/verify-email").orElseThrow();

        assertThat(verify(token).getStatus()).isEqualTo(204);
        MockHttpServletResponse reused = verify(token);

        assertThat(reused.getStatus()).isEqualTo(400);
        assertThat(api.read(reused).get("code").asString()).isEqualTo("TOKEN_INVALID");
        assertThat(api.login(email, PASSWORD, null).getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectVerificationLinkAfter24Hours() throws Exception {
        String email = uniqueEmail();
        api.register(email, PASSWORD);
        String token = emails.latestToken(email, "/verify-email").orElseThrow();
        clock.advance(Duration.ofHours(24).plusSeconds(1));

        MockHttpServletResponse response = verify(token);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(api.read(response).get("code").asString()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void shouldInvalidateOldLinkWhenVerificationIsResent() throws Exception {
        String email = uniqueEmail();
        api.register(email, PASSWORD);
        String oldToken = emails.latestToken(email, "/verify-email").orElseThrow();

        assertThat(postJson("/api/v1/auth/resend-verification", Map.of("email", email)).getStatus()).isEqualTo(202);
        String newToken = emails.latestToken(email, "/verify-email").orElseThrow();

        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(api.read(verify(oldToken)).get("code").asString()).isEqualTo("TOKEN_INVALID");
        assertThat(verify(newToken).getStatus()).isEqualTo(204);
    }

    @Test
    void shouldAcceptResendForUnknownEmailWithoutSendingAnything() throws Exception {
        String email = uniqueEmail();

        assertThat(postJson("/api/v1/auth/resend-verification", Map.of("email", email)).getStatus()).isEqualTo(202);
        assertThat(emails.sentTo(email)).isEmpty();
    }

    // --- Đăng nhập (US-03) ---

    @Test
    void shouldLoginWebClientWithHttpOnlyRefreshCookie() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);

        MockHttpServletResponse response = api.login(email, PASSWORD, "web");

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = api.read(response);
        assertThat(body.get("accessToken").asString()).isNotBlank();
        assertThat(body.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(body.has("refreshToken")).isFalse();
        assertThat(body.get("user").get("needsOnboarding").asBoolean()).isTrue();
        assertThat(body.get("user").get("friendLimit").asInt()).isEqualTo(15);
        assertThat(response.getHeader("Set-Cookie"))
                .contains("moment_rt=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
    }

    @Test
    void shouldReturnRefreshTokenInBodyForNativeClient() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);

        MockHttpServletResponse response = api.login(email, PASSWORD, "native");

        assertThat(api.read(response).get("refreshToken").asString()).isNotBlank();
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void shouldGiveSameErrorForWrongPasswordAndUnknownEmail() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);

        JsonNode wrongPasswordError = api.read(api.login(email, wrongPassword, null));
        JsonNode unknownEmailError = api.read(api.login(uniqueEmail(), wrongPassword, null));

        assertThat(wrongPasswordError.get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknownEmailError.get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPasswordError.get("detail")).isEqualTo(unknownEmailError.get("detail"));
    }

    @Test
    void shouldLockLoginFor15MinutesAfterFiveFailures() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);
        for (int i = 0; i < 5; i++) {
            assertThat(api.login(email, wrongPassword, null).getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse locked = api.login(email, PASSWORD, null);
        assertThat(locked.getStatus()).isEqualTo(429);
        assertThat(api.read(locked).get("code").asString()).isEqualTo("LOGIN_LOCKED");
        assertThat(Long.parseLong(locked.getHeader("Retry-After"))).isBetween(1L, 900L);

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThat(api.login(email, PASSWORD, null).getStatus()).isEqualTo(200);
    }

    @Test
    void shouldPutAuthTimeInAccessToken() throws Exception {
        String email = uniqueEmail();
        String accessToken = api.accessTokenForNewUser(email);

        Jwt jwt = jwtDecoder.decode(accessToken);

        assertThat(((Number) jwt.getClaim("auth_time")).longValue()).isEqualTo(clock.instant().getEpochSecond());
    }

    @Test
    void shouldRejectAccessTokenAfter15Minutes() throws Exception {
        String accessToken = api.accessTokenForNewUser(uniqueEmail());
        assertThat(api.me(accessToken).getStatus()).isEqualTo(200);

        clock.advance(Duration.ofMinutes(16));

        assertThat(api.me(accessToken).getStatus()).isEqualTo(401);
    }

    // --- Refresh token xoay vòng và đăng xuất (danh-sach-api.md mục 2, US-08) ---

    @Test
    void shouldRotateRefreshCookieAndKeepOriginalAuthTime() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);
        Cookie firstCookie = refreshCookie(api.login(email, PASSWORD, "web"));
        long loginTime = clock.instant().getEpochSecond();
        clock.advance(Duration.ofMinutes(20));

        MockHttpServletResponse refreshed = refreshWeb(firstCookie);

        assertThat(refreshed.getStatus()).isEqualTo(200);
        Cookie secondCookie = refreshCookie(refreshed);
        assertThat(secondCookie.getValue()).isNotEqualTo(firstCookie.getValue());
        Jwt jwt = jwtDecoder.decode(api.read(refreshed).get("accessToken").asString());
        assertThat(((Number) jwt.getClaim("auth_time")).longValue()).isEqualTo(loginTime);
    }

    @Test
    void shouldRevokeWholeFamilyWhenRotatedTokenIsReused() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);
        Cookie firstCookie = refreshCookie(api.login(email, PASSWORD, "web"));
        Cookie secondCookie = refreshCookie(refreshWeb(firstCookie));

        MockHttpServletResponse reused = refreshWeb(firstCookie);

        assertThat(reused.getStatus()).isEqualTo(401);
        assertThat(api.read(reused).get("code").asString()).isEqualTo("TOKEN_REUSED");
        MockHttpServletResponse afterRevocation = refreshWeb(secondCookie);
        assertThat(api.read(afterRevocation).get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void shouldRefreshNativeClientFromBody() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);
        String refreshToken = api.read(api.login(email, PASSWORD, "native")).get("refreshToken").asString();

        MockHttpServletResponse response = mockMvc.perform(api.json(post("/api/v1/auth/refresh"),
                        Map.of("refreshToken", refreshToken)).header("X-Client-Type", "native"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(api.read(response).get("refreshToken").asString()).isNotEqualTo(refreshToken);
    }

    @Test
    void shouldRejectRefreshWithoutToken() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/refresh")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(api.read(response).get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void shouldLogoutCurrentDeviceAndClearCookie() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);
        MockHttpServletResponse login = api.login(email, PASSWORD, "web");
        Cookie cookie = refreshCookie(login);
        String accessToken = api.read(login).get("accessToken").asString();

        MockHttpServletResponse logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken).cookie(cookie))
                .andReturn().getResponse();

        assertThat(logout.getStatus()).isEqualTo(204);
        assertThat(logout.getHeader("Set-Cookie")).contains("moment_rt=", "Max-Age=0");
        assertThat(refreshWeb(cookie).getStatus()).isEqualTo(401);
    }

    @Test
    void shouldRequireAccessTokenToLogout() throws Exception {
        assertThat(mockMvc.perform(post("/api/v1/auth/logout")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    // --- Quên và đặt lại mật khẩu (US-05) ---

    @Test
    void shouldAcceptForgotPasswordForUnknownEmailWithoutSendingAnything() throws Exception {
        String email = uniqueEmail();

        assertThat(postJson("/api/v1/auth/forgot-password", Map.of("email", email)).getStatus()).isEqualTo(202);
        assertThat(emails.sentTo(email)).isEmpty();
    }

    @Test
    void shouldResetPasswordAndRevokeAllSessions() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);
        Cookie oldSession = refreshCookie(api.login(email, PASSWORD, "web"));
        postJson("/api/v1/auth/forgot-password", Map.of("email", email));
        String token = emails.latestToken(email, "/reset-password").orElseThrow();

        MockHttpServletResponse reset = postJson("/api/v1/auth/reset-password",
                Map.of("token", token, "newPassword", newPassword));

        assertThat(reset.getStatus()).isEqualTo(204);
        assertThat(refreshWeb(oldSession).getStatus()).isEqualTo(401);
        assertThat(api.login(email, PASSWORD, null).getStatus()).isEqualTo(401);
        assertThat(api.login(email, newPassword, null).getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectResetLinkAfterOneHour() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);
        postJson("/api/v1/auth/forgot-password", Map.of("email", email));
        String token = emails.latestToken(email, "/reset-password").orElseThrow();
        clock.advance(Duration.ofHours(1).plusSeconds(1));

        MockHttpServletResponse reset = postJson("/api/v1/auth/reset-password",
                Map.of("token", token, "newPassword", newPassword));

        assertThat(api.read(reset).get("code").asString()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void shouldNotAllowDemoAccountToResetPassword() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);
        jdbcTemplate.update("UPDATE users SET is_demo = true WHERE lower(email) = lower(?)", email);
        postJson("/api/v1/auth/forgot-password", Map.of("email", email));
        String token = emails.latestToken(email, "/reset-password").orElseThrow();

        MockHttpServletResponse reset = postJson("/api/v1/auth/reset-password",
                Map.of("token", token, "newPassword", newPassword));

        assertThat(reset.getStatus()).isEqualTo(403);
        assertThat(api.read(reset).get("code").asString()).isEqualTo("DEMO_ACCOUNT_READONLY");
        assertThat(api.login(email, PASSWORD, null).getStatus()).isEqualTo(200);
    }

    // --- Giới hạn tần suất (danh-sach-api.md mục 6) ---

    @Test
    void shouldRateLimitRegistrationToFivePerHourPerIp() throws Exception {
        var sameIp = randomIp();
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse ok = mockMvc.perform(api.json(post("/api/v1/auth/register"),
                    Map.of("email", uniqueEmail(), "password", PASSWORD)).with(sameIp)).andReturn().getResponse();
            assertThat(ok.getStatus()).isEqualTo(201);
        }

        MockHttpServletResponse limited = mockMvc.perform(api.json(post("/api/v1/auth/register"),
                Map.of("email", uniqueEmail(), "password", PASSWORD)).with(sameIp)).andReturn().getResponse();

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(api.read(limited).get("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void shouldRateLimitForgotPasswordToThreePerHourPerEmail() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 3; i++) {
            assertThat(postJson("/api/v1/auth/forgot-password", Map.of("email", email)).getStatus()).isEqualTo(202);
        }

        assertThat(postJson("/api/v1/auth/forgot-password", Map.of("email", email)).getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse verify(String token) throws Exception {
        return postJson("/api/v1/auth/verify-email", Map.of("token", token));
    }

    private MockHttpServletResponse postJson(String path, Map<String, ?> body) throws Exception {
        return mockMvc.perform(api.json(post(path), body).with(randomIp())).andReturn().getResponse();
    }

    private MockHttpServletResponse refreshWeb(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andReturn().getResponse();
    }

    private static Cookie refreshCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie("moment_rt");
        assertThat(cookie).as("cookie moment_rt").isNotNull();
        return cookie;
    }
}
