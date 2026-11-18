package com.dev.moment.demo;

import static com.dev.moment.support.ApiTestClient.newPassword;
import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.dev.moment.IntegrationTest;
import com.dev.moment.media.ObjectStorage;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MomentFixtures;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-163: dựng lại dữ liệu tài khoản demo (US-51, D-027, D-045). */
@IntegrationTest
class DemoSeederTests {

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
    @Autowired
    private ObjectStorage storage;
    @Autowired
    private DemoSeeder seeder;

    private ApiTestClient api;
    // Email và mật khẩu demo sinh lúc chạy như khi đọc từ biến môi trường; không viết cứng trong code.
    private final String email = uniqueEmail();
    private final String password = newPassword();

    @BeforeEach
    void setUp() {
        clock.reset();
        api = new ApiTestClient(mockMvc, jsonMapper, emails);
    }

    // US-51: đăng nhập tài khoản demo có sẵn bạn bè và khoảnh khắc.
    @Test
    void shouldCreateDemoAccountWithFriendsAndMomentsToProcess() throws Exception {
        seeder.reseed(email, password);

        MockHttpServletResponse login = api.login(email, password, "native");
        assertThat(login.getStatus()).as(login.getContentAsString()).isEqualTo(200);
        JsonNode me = api.read(api.me(api.read(login).get("accessToken").asString()));
        assertThat(me.get("isDemo").asBoolean()).isTrue();
        assertThat(me.get("username").asString()).isEqualTo("demo");
        assertThat(me.get("friendCount").asInt()).isEqualTo(3);

        assertThat(count("SELECT count(*) FROM users WHERE is_demo")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM moments m JOIN users u ON u.id = m.owner_id WHERE u.is_demo"))
                .isEqualTo(5);
        // Khoảnh khắc chờ worker xử lý như một lần đăng thật (ADR 0003).
        assertThat(count("""
                SELECT count(*) FROM moments m JOIN users u ON u.id = m.owner_id
                JOIN media_jobs j ON j.moment_id = m.id AND j.type = 'PROCESS_MOMENT' AND j.status = 'PENDING'
                WHERE u.is_demo AND m.status = 'PROCESSING'
                """)).isEqualTo(5);
        List<String> keys = demoObjectKeys();
        assertThat(keys).hasSize(10);
        for (String key : keys) {
            assertThat(storage.find(key)).as(key).isPresent();
        }
        assertThat(count("""
                SELECT count(*) FROM reactions r JOIN moments m ON m.id = r.moment_id JOIN users u ON u.id = m.owner_id
                WHERE u.is_demo
                """)).isEqualTo(3);
    }

    // D-027: chạy lại hằng ngày thì thay toàn bộ dữ liệu demo, file cũ xóa qua hàng đợi, dữ liệu thật không đổi.
    @Test
    void shouldReplaceDemoDataOnReseedWithoutTouchingRealUsers() throws Exception {
        MomentFixtures fixtures = new MomentFixtures(mockMvc, api, jdbcTemplate);
        MomentFixtures.TestUser real = fixtures.newUser();
        fixtures.befriend(real);
        String realMoment = fixtures.readyPhotoForAll(real);
        seeder.reseed(email, password);
        List<String> firstKeys = demoObjectKeys();

        seeder.reseed(email, password);

        assertThat(count("SELECT count(*) FROM users WHERE is_demo")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM moments m JOIN users u ON u.id = m.owner_id WHERE u.is_demo"))
                .isEqualTo(5);
        assertThat(demoObjectKeys()).doesNotContainAnyElementsOf(firstKeys);
        List<String> queued = jdbcTemplate.queryForList(
                "SELECT jsonb_array_elements_text(payload -> 'keys') FROM media_jobs WHERE type = 'DELETE_OBJECTS'",
                String.class);
        assertThat(queued).containsAll(firstKeys);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM moments WHERE id = ?", Integer.class,
                UUID.fromString(realMoment))).isEqualTo(1);
        assertThat(api.login(email, password, "native").getStatus()).isEqualTo(200);
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private List<String> demoObjectKeys() {
        return jdbcTemplate.queryForList("""
                SELECT f.object_key FROM moment_media f JOIN moments m ON m.id = f.moment_id
                JOIN users u ON u.id = m.owner_id WHERE u.is_demo
                """, String.class);
    }
}
