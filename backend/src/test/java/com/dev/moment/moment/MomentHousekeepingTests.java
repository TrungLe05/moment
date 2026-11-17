package com.dev.moment.moment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MomentFixtures;
import com.dev.moment.support.MomentFixtures.TestUser;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-161: job định kỳ của khoảnh khắc (DB mục 9, BR-14, D-029): hết hạn, dọn bản nháp, dọn khoảnh khắc lỗi. */
@IntegrationTest
class MomentHousekeepingTests {

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
    private MomentHousekeeping housekeeping;

    private ApiTestClient api;
    private MomentFixtures fixtures;

    @BeforeEach
    void setUp() {
        clock.reset();
        api = new ApiTestClient(mockMvc, jsonMapper, emails);
        fixtures = new MomentFixtures(mockMvc, api, jdbcTemplate);
    }

    // BR-14: hết 24 giờ thì file bị xóa, bản ghi giữ ở trạng thái hết hạn.
    @Test
    void shouldExpireSharedMomentPast24HoursAndQueueItsFilesForDeletion() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String moment = fixtures.readyPhotoForAll(owner);
        List<String> keys = objectKeys(moment);
        fixtures.shiftIntoPast(moment, Duration.ofHours(24));

        housekeeping.expireShared();

        assertThat(status(moment)).isEqualTo("EXPIRED");
        assertThat(objectKeys(moment)).isEmpty();
        assertThat(queuedDeletions(moment)).containsExactlyInAnyOrderElementsOf(keys);
        // Người nhận không còn thấy; chủ vẫn thấy bản ghi nhưng không còn file.
        assertThat(fixtures.as(get("/api/v1/moments/{id}", moment), friend).getStatus()).isEqualTo(404);
        JsonNode mine = api.read(fixtures.as(get("/api/v1/moments/{id}", moment), owner));
        assertThat(mine.get("status").asString()).isEqualTo("EXPIRED");
        assertThat(mine.get("media").isNull()).isTrue();
    }

    @Test
    void shouldExpireSharedMomentsThatAreStillProcessingOrFailed() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String processing = fixtures.draftPhoto(owner, "ALL", List.of());
        setStatus(processing, "PROCESSING");
        String failed = fixtures.draftPhoto(owner, "ALL", List.of());
        setStatus(failed, "FAILED");
        fixtures.shiftIntoPast(processing, Duration.ofHours(25));
        fixtures.shiftIntoPast(failed, Duration.ofHours(25));

        housekeeping.expireShared();

        assertThat(status(processing)).isEqualTo("EXPIRED");
        assertThat(status(failed)).isEqualTo("EXPIRED");
        assertThat(queuedDeletions(processing)).hasSize(2);
    }

    // BR-15: khoảnh khắc lưu giữ không bao giờ hết hạn; khoảnh khắc chia sẻ còn trong 24 giờ thì chưa.
    @Test
    void shouldLeaveKeptAndFreshMomentsAlone() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String kept = fixtures.readyPhotoForAll(owner);
        fixtures.keep(kept);
        List<String> keptKeys = objectKeys(kept);
        fixtures.shiftIntoPast(kept, Duration.ofDays(3));
        String fresh = fixtures.readyPhotoForAll(owner);
        fixtures.shiftIntoPast(fresh, Duration.ofHours(23));

        housekeeping.expireShared();

        assertThat(status(kept)).isEqualTo("READY");
        assertThat(status(fresh)).isEqualTo("READY");
        assertThat(objectKeys(kept)).containsExactlyInAnyOrderElementsOf(keptKeys);
        assertThat(queuedDeletions(kept)).isEmpty();
    }

    // BR-16: hết hạn rồi thì không chuyển sang lưu giữ được nữa.
    @Test
    void shouldNotLetOwnerKeepAnExpiredMoment() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String moment = fixtures.readyPhotoForAll(owner);
        fixtures.shiftIntoPast(moment, Duration.ofHours(24));
        housekeeping.expireShared();

        MockHttpServletResponse keep = fixtures.as(post("/api/v1/moments/{id}/keep", moment), owner);

        assertThat(keep.getStatus()).isEqualTo(409);
        assertThat(api.read(keep).get("code").asString()).isEqualTo("MOMENT_NOT_KEEPABLE");
    }

    // DB mục 4: bản nháp quá 1 giờ chưa tải xong thì xóa, kèm file đã lỡ tải lên.
    @Test
    void shouldDeleteDraftsOlderThanOneHour() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String stale = fixtures.draftPhoto(owner, "ALL", List.of());
        List<String> staleKeys = objectKeys(stale);
        fixtures.shiftIntoPast(stale, Duration.ofMinutes(61));
        String recent = fixtures.draftPhoto(owner, "ALL", List.of());
        fixtures.shiftIntoPast(recent, Duration.ofMinutes(59));

        housekeeping.deleteStaleDrafts();

        assertThat(exists(stale)).isFalse();
        assertThat(queuedDeletions(stale)).containsExactlyInAnyOrderElementsOf(staleKeys);
        assertThat(exists(recent)).isTrue();
        assertThat(fixtures.as(post("/api/v1/moments/{id}/complete", stale), owner).getStatus()).isEqualTo(404);
    }

    // D-029: khoảnh khắc FAILED tạo quá 7 ngày bị xóa ở mọi chế độ.
    @Test
    void shouldDeleteFailedMomentsOlderThanSevenDaysInAnyMode() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String keptFailed = fixtures.draftPhoto(owner, "ALL", List.of());
        setStatus(keptFailed, "FAILED");
        fixtures.keep(keptFailed);
        List<String> keys = objectKeys(keptFailed);
        fixtures.shiftIntoPast(keptFailed, Duration.ofDays(7).plusMinutes(1));
        String recentFailed = fixtures.draftPhoto(owner, "ALL", List.of());
        setStatus(recentFailed, "FAILED");
        fixtures.keep(recentFailed);
        fixtures.shiftIntoPast(recentFailed, Duration.ofDays(6));

        housekeeping.deleteStaleFailed();

        assertThat(exists(keptFailed)).isFalse();
        assertThat(queuedDeletions(keptFailed)).containsExactlyInAnyOrderElementsOf(keys);
        assertThat(exists(recentFailed)).isTrue();
    }

    private void setStatus(String momentId, String status) {
        jdbcTemplate.update("UPDATE moments SET status = ? WHERE id = ?::uuid", status, momentId);
    }

    private String status(String momentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM moments WHERE id = ?::uuid", String.class, momentId);
    }

    private boolean exists(String momentId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM moments WHERE id = ?::uuid)", Boolean.class, momentId));
    }

    private List<String> objectKeys(String momentId) {
        return jdbcTemplate.queryForList("SELECT object_key FROM moment_media WHERE moment_id = ?::uuid", String.class,
                momentId);
    }

    private List<String> queuedDeletions(String momentId) {
        return jdbcTemplate.queryForList("""
                SELECT jsonb_array_elements_text(payload -> 'keys') FROM media_jobs
                WHERE type = 'DELETE_OBJECTS' AND moment_id = ?::uuid
                """, String.class, momentId);
    }
}
