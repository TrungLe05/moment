package com.dev.moment.moment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MomentFixtures;
import com.dev.moment.support.MomentFixtures.TestUser;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-144: chuyển sang lưu giữ và xóa khoảnh khắc (US-34, US-35, BR-16, BR-18, API mục 3.4). */
@IntegrationTest
class MomentKeepDeleteTests {

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

    // --- Lưu giữ (BR-16) ---

    @Test
    void shouldKeepSharedMomentWithin24Hours() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);
        fixtures.shiftIntoPast(momentId, Duration.ofHours(23));

        MockHttpServletResponse response = keep(momentId, owner);

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode moment = api.read(response);
        assertThat(moment.get("mode").asString()).isEqualTo("KEPT");
        assertThat(Instant.parse(moment.get("keptAt").asString())).isEqualTo(clock.instant());
        assertThat(moment.get("ownerView")).isNotNull();
    }

    // BR-15: sau khi lưu giữ, người nhận vẫn xem được khi đã quá 24 giờ.
    @Test
    void shouldStayVisibleToRecipientAfter24HoursOnceKept() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);
        keep(momentId, owner);
        fixtures.shiftIntoPast(momentId, Duration.ofHours(30));

        assertThat(fixtures.as(get("/api/v1/moments/{id}", momentId), friend).getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAnswerOkWithoutChangesWhenAlreadyKept() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);
        fixtures.keep(momentId);
        String keptAt = api.read(fixtures.as(get("/api/v1/moments/{id}", momentId), owner)).get("keptAt").asString();
        clock.advance(Duration.ofMinutes(5));

        MockHttpServletResponse response = keep(momentId, owner);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(api.read(response).get("keptAt").asString()).isEqualTo(keptAt);
    }

    @Test
    void shouldRefuseToKeepAfter24Hours() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);
        fixtures.shiftIntoPast(momentId, Duration.ofHours(24));

        MockHttpServletResponse response = keep(momentId, owner);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(api.read(response).get("code").asString()).isEqualTo("MOMENT_NOT_KEEPABLE");
    }

    // DB mục 7: chỉ PROCESSING và READY chuyển được; bản nháp, lỗi, hết hạn thì không.
    @Test
    void shouldRefuseToKeepDraftFailedOrExpiredMoments() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String momentId = fixtures.draftPhoto(owner, "ALL", List.of());

        for (String status : List.of("DRAFT", "FAILED", "EXPIRED")) {
            jdbcTemplate.update("UPDATE moments SET status = ? WHERE id = ?::uuid", status, momentId);
            MockHttpServletResponse response = keep(momentId, owner);
            assertThat(response.getStatus()).as(status).isEqualTo(409);
        }
        jdbcTemplate.update("UPDATE moments SET status = 'PROCESSING' WHERE id = ?::uuid", momentId);
        assertThat(keep(momentId, owner).getStatus()).isEqualTo(200);
    }

    @Test
    void shouldHideKeepFromEveryoneButTheOwner() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);

        assertThat(keep(momentId, friend).getStatus()).isEqualTo(404);
        assertThat(keep(momentId, fixtures.newUser()).getStatus()).isEqualTo(404);
    }

    // --- Xóa (BR-18) ---

    @Test
    void shouldDeleteMomentForEveryoneWithReactionsAndQueueFileRemoval() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);
        fixtures.as(api.json(put("/api/v1/moments/{id}/reaction", momentId), Map.of("emoji", "🔥")), friend);
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT object_key FROM moment_media WHERE moment_id = ?::uuid", String.class, momentId);
        assertThat(keys).hasSize(4);

        MockHttpServletResponse response = remove(momentId, owner);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(fixtures.as(get("/api/v1/moments/{id}", momentId), friend).getStatus()).isEqualTo(404);
        assertThat(fixtures.as(get("/api/v1/moments/{id}", momentId), owner).getStatus()).isEqualTo(404);
        assertThat(count("SELECT count(*) FROM moments WHERE id = ?::uuid", momentId)).isZero();
        assertThat(count("SELECT count(*) FROM reactions WHERE moment_id = ?::uuid", momentId)).isZero();
        assertThat(count("SELECT count(*) FROM moment_recipients WHERE moment_id = ?::uuid", momentId)).isZero();
        // File trên storage xóa bất đồng bộ qua hàng đợi, không gọi storage trong transaction (DB mục 2).
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload::text FROM media_jobs WHERE moment_id = ?::uuid AND type = 'DELETE_OBJECTS'",
                String.class, momentId);
        for (String key : keys) {
            assertThat(payload).contains(key);
        }
    }

    // BR-18: xóa được ở mọi trạng thái, kể cả bản nháp chưa tải file.
    @Test
    void shouldDeleteMomentInAnyStatus() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String draft = fixtures.draftPhoto(owner, "ALL", List.of());
        String expired = fixtures.readyPhotoForAll(owner);
        jdbcTemplate.update("UPDATE moments SET status = 'EXPIRED' WHERE id = ?::uuid", expired);

        assertThat(remove(draft, owner).getStatus()).isEqualTo(204);
        assertThat(remove(expired, owner).getStatus()).isEqualTo(204);
    }

    @Test
    void shouldHideDeleteFromEveryoneButTheOwner() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);

        assertThat(remove(momentId, friend).getStatus()).isEqualTo(404);
        assertThat(remove(momentId, fixtures.newUser()).getStatus()).isEqualTo(404);
        assertThat(count("SELECT count(*) FROM moments WHERE id = ?::uuid", momentId)).isEqualTo(1);
    }

    @Test
    void shouldAnswerNotFoundWhenDeletingTwice() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);

        assertThat(remove(momentId, owner).getStatus()).isEqualTo(204);
        assertThat(remove(momentId, owner).getStatus()).isEqualTo(404);
    }

    private MockHttpServletResponse keep(String momentId, TestUser user) throws Exception {
        return fixtures.as(post("/api/v1/moments/{id}/keep", momentId), user);
    }

    private MockHttpServletResponse remove(String momentId, TestUser user) throws Exception {
        return fixtures.as(delete("/api/v1/moments/{id}", momentId), user);
    }

    private int count(String sql, String momentId) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, momentId);
        return value == null ? 0 : value;
    }
}
