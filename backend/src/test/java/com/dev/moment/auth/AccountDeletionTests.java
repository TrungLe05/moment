package com.dev.moment.auth;

import static com.dev.moment.support.ApiTestClient.PASSWORD;
import static com.dev.moment.support.ApiTestClient.randomIp;
import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MomentFixtures;
import com.dev.moment.support.MomentFixtures.TestUser;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** T-118: xóa tài khoản và toàn bộ dữ liệu (US-09, D-026, D-027, API mục 3.2). */
@IntegrationTest
class AccountDeletionTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RecordingEmailSender emails;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;

    private ApiTestClient api;
    private MomentFixtures fixtures;

    @BeforeEach
    void setUp() {
        clock.reset();
        api = new ApiTestClient(mockMvc, jsonMapper, emails);
        fixtures = new MomentFixtures(mockMvc, api, jdbcTemplate);
    }

    // US-09: xóa mọi khoảnh khắc, file, reaction, quan hệ bạn bè của người đó.
    @Test
    void shouldDeleteAccountAndAllItsDataAfterPasswordConfirmation() throws Exception {
        TestUser me = fixtures.newUser();
        TestUser friend = fixtures.befriend(me);
        String moment = fixtures.readyPhotoForAll(me);
        List<String> keys = jdbcTemplate.queryForList("SELECT object_key FROM moment_media WHERE moment_id = ?::uuid",
                String.class, moment);

        MockHttpServletResponse deleted = deleteMe(me, Map.of("password", PASSWORD));

        assertThat(deleted.getStatus()).as(deleted.getContentAsString()).isEqualTo(204);
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", me.id())).isZero();
        assertThat(count("SELECT count(*) FROM moments WHERE owner_id = ?", me.id())).isZero();
        assertThat(count("SELECT count(*) FROM friendships WHERE user_a_id = ? OR user_b_id = ?", me.id(), me.id()))
                .isZero();
        assertThat(jdbcTemplate.queryForList("""
                SELECT jsonb_array_elements_text(payload -> 'keys') FROM media_jobs
                WHERE type = 'DELETE_OBJECTS' AND moment_id IS NULL
                """, String.class)).containsAll(keys);
        // Phiên cũ không dùng được nữa; người bạn còn lại thấy 0 bạn.
        assertThat(api.me(me.accessToken()).getStatus()).isEqualTo(401);
        assertThat(api.read(api.me(friend.accessToken())).get("friendCount").asInt()).isZero();
    }

    @Test
    void shouldRefuseWithoutTheRightPassword() throws Exception {
        TestUser me = fixtures.newUser();

        for (Map<String, Object> body : List.<Map<String, Object>>of(Map.of("password", "wrong-" + UUID.randomUUID()),
                Map.of())) {
            MockHttpServletResponse refused = deleteMe(me, body);
            assertThat(refused.getStatus()).as(body.toString()).isEqualTo(401);
            assertThat(api.read(refused).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        }
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", me.id())).isEqualTo(1);
    }

    // D-026: tài khoản chỉ có Google phải đăng nhập lại trong 5 phút gần nhất.
    @Test
    void shouldAskGoogleOnlyAccountsToSignInAgainAfterFiveMinutes() throws Exception {
        TestUser stale = googleUser();
        clock.advance(Duration.ofMinutes(6));
        MockHttpServletResponse refused = deleteMe(stale, Map.of());
        assertThat(refused.getStatus()).isEqualTo(403);
        assertThat(api.read(refused).get("code").asString()).isEqualTo("REAUTH_REQUIRED");

        clock.reset();
        TestUser fresh = googleUser();
        assertThat(deleteMe(fresh, Map.of()).getStatus()).isEqualTo(204);
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", fresh.id())).isZero();
    }

    // D-027: tài khoản demo không xóa được.
    @Test
    void shouldNotDeleteTheDemoAccount() throws Exception {
        TestUser demo = fixtures.newUser();
        jdbcTemplate.update("UPDATE users SET is_demo = true WHERE id = ?", demo.id());

        MockHttpServletResponse refused = deleteMe(demo, Map.of("password", PASSWORD));

        assertThat(refused.getStatus()).isEqualTo(403);
        assertThat(api.read(refused).get("code").asString()).isEqualTo("DEMO_ACCOUNT_READONLY");
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", demo.id())).isEqualTo(1);
    }

    // API mục 2: bản web giữ refresh token trong cookie; xóa tài khoản thì xóa luôn cookie.
    @Test
    void shouldClearTheRefreshCookieForWebClients() throws Exception {
        TestUser me = fixtures.newUser();

        MockHttpServletResponse deleted = mockMvc.perform(api.json(delete("/api/v1/me"), Map.of("password", PASSWORD))
                .header("Authorization", "Bearer " + me.accessToken()).header("X-Client-Type", "web"))
                .andReturn().getResponse();

        assertThat(deleted.getStatus()).isEqualTo(204);
        assertThat(deleted.getHeader(HttpHeaders.SET_COOKIE)).contains("moment_rt=").contains("Max-Age=0");
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        assertThat(mockMvc.perform(api.json(delete("/api/v1/me"), Map.of())).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    private MockHttpServletResponse deleteMe(TestUser user, Map<String, Object> body) throws Exception {
        return mockMvc.perform(api.json(delete("/api/v1/me"), body)
                .header("Authorization", "Bearer " + user.accessToken())).andReturn().getResponse();
    }

    // Đăng nhập Google lần đầu tạo tài khoản không có mật khẩu (FakeGoogleTokenVerifier).
    private TestUser googleUser() throws Exception {
        String email = uniqueEmail();
        Map<String, Object> body = new HashMap<>();
        body.put("idToken", "fake-google|" + UUID.randomUUID() + "|" + email + "|true");
        MockHttpServletResponse login = mockMvc.perform(api.json(post("/api/v1/auth/google"), body)
                .header("X-Client-Type", "native").with(randomIp())).andReturn().getResponse();
        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(200);
        String accessToken = api.read(login).get("accessToken").asString();
        return new TestUser(UUID.fromString(api.read(api.me(accessToken)).get("id").asString()), accessToken);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
