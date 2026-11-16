package com.dev.moment.auth;

import static com.dev.moment.support.ApiTestClient.PASSWORD;
import static com.dev.moment.support.ApiTestClient.randomIp;
import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.RecordingEmailSender;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** US-04, BR-03 (D-019), locale cho tài khoản mới (D-023). */
@IntegrationTest
class GoogleLoginTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RecordingEmailSender emails;

    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, jsonMapper, emails);
    }

    @Test
    void shouldCreateAccountOnFirstGoogleLoginWithRequestedLocale() throws Exception {
        String email = uniqueEmail();

        MockHttpServletResponse response = google(token(UUID.randomUUID().toString(), email, true), "en");

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = api.read(response);
        assertThat(body.get("isNewUser").asBoolean()).isTrue();
        assertThat(body.get("user").get("needsOnboarding").asBoolean()).isTrue();
        assertThat(body.get("user").get("locale").asString()).isEqualTo("en");
    }

    @Test
    void shouldReturnSameAccountOnSecondGoogleLogin() throws Exception {
        String subject = UUID.randomUUID().toString();
        String email = uniqueEmail();
        JsonNode first = api.read(google(token(subject, email, true), null));

        JsonNode second = api.read(google(token(subject, email, true), null));

        assertThat(second.get("isNewUser").asBoolean()).isFalse();
        assertThat(second.get("user").get("id")).isEqualTo(first.get("user").get("id"));
        assertThat(first.get("user").get("locale").asString()).isEqualTo("vi");
    }

    @Test
    void shouldLinkGoogleToVerifiedEmailAccount() throws Exception {
        String email = uniqueEmail();
        api.registerAndVerify(email);
        String existingId = api.read(api.login(email, PASSWORD, null)).get("user").get("id").asString();

        JsonNode body = api.read(google(token(UUID.randomUUID().toString(), email.toUpperCase(), true), null));

        assertThat(body.get("isNewUser").asBoolean()).isFalse();
        assertThat(body.get("user").get("id").asString()).isEqualTo(existingId);
        assertThat(api.login(email, PASSWORD, null).getStatus()).isEqualTo(200);
    }

    @Test
    void shouldClaimUnverifiedEmailAccountAndDropItsPassword() throws Exception {
        String email = uniqueEmail();
        api.register(email, PASSWORD);

        JsonNode body = api.read(google(token(UUID.randomUUID().toString(), email, true), null));

        assertThat(body.get("isNewUser").asBoolean()).isFalse();
        // Mật khẩu do người đăng ký trước đặt không còn dùng được.
        assertThat(api.read(api.login(email, PASSWORD, null)).get("code").asString())
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void shouldRejectGoogleAccountWithUnverifiedEmail() throws Exception {
        MockHttpServletResponse response = google(token(UUID.randomUUID().toString(), uniqueEmail(), false), null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(api.read(response).get("code").asString()).isEqualTo("GOOGLE_TOKEN_INVALID");
    }

    @Test
    void shouldRejectInvalidGoogleToken() throws Exception {
        MockHttpServletResponse response = google("not-a-google-token", null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(api.read(response).get("code").asString()).isEqualTo("GOOGLE_TOKEN_INVALID");
    }

    private MockHttpServletResponse google(String idToken, String locale) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("idToken", idToken);
        if (locale != null) {
            body.put("locale", locale);
        }
        return mockMvc.perform(api.json(post("/api/v1/auth/google"), body).with(randomIp()))
                .andReturn().getResponse();
    }

    private static String token(String subject, String email, boolean emailVerified) {
        return "fake-google|" + subject + "|" + email + "|" + emailVerified;
    }
}
