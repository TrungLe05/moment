package com.dev.moment.moment;

import static com.dev.moment.support.ApiTestClient.randomIp;
import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-141: chi tiết khoảnh khắc qua MomentAccessPolicy (BR-13, BR-19, BR-20, API mục 4). */
@IntegrationTest
class MomentDetailTests {

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

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, jsonMapper, emails);
        clock.reset();
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    // --- Chủ ---

    @Test
    void shouldShowOwnerTheirDraftWithoutMediaUrls() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        String momentId = createPhoto(owner, "ALL", List.of());

        MockHttpServletResponse response = detail(momentId, owner);

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode moment = api.read(response);
        assertThat(moment.get("isMine").asBoolean()).isTrue();
        assertThat(moment.get("status").asString()).isEqualTo("DRAFT");
        assertThat(moment.get("media").isNull()).isTrue();
        assertThat(moment.get("myReaction").isNull()).isTrue();
        assertThat(moment.get("ownerView").get("audience").asString()).isEqualTo("ALL");
        assertThat(moment.get("ownerView").get("recipientCount").asInt()).isEqualTo(1);
    }

    @Test
    void shouldShowOwnerFailureReason() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        String momentId = createPhoto(owner, "ALL", List.of());
        jdbcTemplate.update("UPDATE moments SET status = 'FAILED', failure_reason = 'VIDEO_TOO_LONG' WHERE id = ?::uuid",
                momentId);

        JsonNode moment = api.read(detail(momentId, owner));

        assertThat(moment.get("status").asString()).isEqualTo("FAILED");
        assertThat(moment.get("ownerView").get("failureReason").asString()).isEqualTo("VIDEO_TOO_LONG");
    }

    @Test
    void shouldKeepShowingOwnerTheirSharedMomentAfter24Hours() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        String momentId = readyPhoto(owner, "ALL", List.of());
        shiftIntoPast(momentId, Duration.ofHours(25));

        assertThat(detail(momentId, owner).getStatus()).isEqualTo(200);
    }

    // --- Người nhận ---

    @Test
    void shouldShowReadyMomentToRecipientWithMediaUrls() throws Exception {
        TestUser owner = newUser();
        TestUser friend = befriend(owner);
        String momentId = readyPhoto(owner, "ALL", List.of());

        MockHttpServletResponse response = detail(momentId, friend);

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode moment = api.read(response);
        assertThat(moment.get("isMine").asBoolean()).isFalse();
        assertThat(moment.get("owner").get("id").asString()).isEqualTo(owner.id().toString());
        assertThat(moment.get("ownerView").isNull()).isTrue();
        assertThat(moment.get("width").asInt()).isEqualTo(1440);
        JsonNode media = moment.get("media");
        assertThat(media.get("displayUrl").asString()).startsWith("http").contains("display-");
        assertThat(media.get("thumbnailUrl").asString()).startsWith("http").contains("thumbnail-");
        assertThat(media.get("videoUrl").isNull()).isTrue();
        // BR-20: URL đọc file hết hạn sau 10 phút.
        assertThat(Instant.parse(media.get("urlsExpireAt").asString()))
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
    }

    // BR-13: chỉ hiện với người nhận khi đã xử lý xong.
    @Test
    void shouldHideMomentFromRecipientUntilReady() throws Exception {
        TestUser owner = newUser();
        TestUser friend = befriend(owner);
        String momentId = createPhoto(owner, "ALL", List.of());

        for (String status : List.of("DRAFT", "PROCESSING", "FAILED")) {
            jdbcTemplate.update("UPDATE moments SET status = ? WHERE id = ?::uuid", status, momentId);
            assertThat(detail(momentId, friend).getStatus()).as(status).isEqualTo(404);
        }
    }

    @Test
    void shouldHideMomentFromFriendWhoIsNotRecipient() throws Exception {
        TestUser owner = newUser();
        TestUser chosen = befriend(owner);
        TestUser other = befriend(owner);
        String momentId = readyPhoto(owner, "SELECTED", List.of(chosen.id().toString()));

        assertThat(detail(momentId, chosen).getStatus()).isEqualTo(200);
        MockHttpServletResponse hidden = detail(momentId, other);
        assertThat(hidden.getStatus()).isEqualTo(404);
        assertThat(api.read(hidden).get("code").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldHideMomentFromStranger() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        String momentId = readyPhoto(owner, "ALL", List.of());

        assertThat(detail(momentId, newUser()).getStatus()).isEqualTo(404);
    }

    // BR-19: người nhận phải đang là bạn; hủy kết bạn thì mất quyền xem kể cả khoảnh khắc lưu giữ.
    @Test
    void shouldHideMomentFromRecipientAfterUnfriending() throws Exception {
        TestUser owner = newUser();
        TestUser friend = befriend(owner);
        String momentId = readyPhoto(owner, "ALL", List.of());
        jdbcTemplate.update("UPDATE moments SET mode = 'KEPT', kept_at = created_at WHERE id = ?::uuid", momentId);

        mockMvc.perform(delete("/api/v1/friends/{id}", friend.id()).header("Authorization", "Bearer " + owner.accessToken()));

        assertThat(detail(momentId, friend).getStatus()).isEqualTo(404);
    }

    // BR-14, BR-19: khoảnh khắc chia sẻ chỉ xem được trước expires_at; đúng mốc 24 giờ là đã hết.
    @Test
    void shouldHideSharedMomentFromRecipientOnceWindowEnds() throws Exception {
        TestUser owner = newUser();
        TestUser friend = befriend(owner);
        String momentId = readyPhoto(owner, "ALL", List.of());

        shiftIntoPast(momentId, Duration.ofHours(24).minusSeconds(1));
        assertThat(detail(momentId, friend).getStatus()).isEqualTo(200);
        shiftIntoPast(momentId, Duration.ofSeconds(1));
        assertThat(detail(momentId, friend).getStatus()).isEqualTo(404);
    }

    // BR-15: khoảnh khắc lưu giữ vẫn xem được sau 24 giờ.
    @Test
    void shouldKeepShowingKeptMomentToRecipientAfter24Hours() throws Exception {
        TestUser owner = newUser();
        TestUser friend = befriend(owner);
        String momentId = readyPhoto(owner, "ALL", List.of());
        jdbcTemplate.update("UPDATE moments SET mode = 'KEPT', kept_at = created_at WHERE id = ?::uuid", momentId);

        shiftIntoPast(momentId, Duration.ofDays(30));

        assertThat(detail(momentId, friend).getStatus()).isEqualTo(200);
    }

    // --- Lỗi chung ---

    @Test
    void shouldReturnNotFoundForUnknownMoment() throws Exception {
        assertThat(detail(UUID.randomUUID().toString(), newUser()).getStatus()).isEqualTo(404);
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/moments/{id}", UUID.randomUUID()))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // --- Tiện ích ---

    private record TestUser(UUID id, String accessToken) {
    }

    /**
     * Lùi created_at và expires_at thay vì tua đồng hồ: tua 24 giờ thì access token (15 phút) của người xem hết hạn.
     */
    private void shiftIntoPast(String momentId, Duration age) {
        jdbcTemplate.update("UPDATE moments SET created_at = created_at - ?::interval, "
                + "expires_at = expires_at - ?::interval, kept_at = kept_at - ?::interval WHERE id = ?::uuid",
                age.toSeconds() + " seconds", age.toSeconds() + " seconds", age.toSeconds() + " seconds", momentId);
    }

    private MockHttpServletResponse detail(String momentId, TestUser viewer) throws Exception {
        return mockMvc.perform(get("/api/v1/moments/{id}", momentId)
                .header("Authorization", "Bearer " + viewer.accessToken())).andReturn().getResponse();
    }

    private String createPhoto(TestUser owner, String audience, List<String> recipientIds) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("mediaType", "PHOTO");
        body.put("source", "CAMERA");
        body.put("mode", "SHARED");
        body.put("audience", audience);
        body.put("recipientIds", recipientIds);
        body.put("layers", List.of());
        body.put("layersVersion", 1);
        body.put("files", List.of(
                Map.of("variant", "ORIGINAL", "contentType", "image/jpeg", "sizeBytes", 2048),
                Map.of("variant", "COMPOSED", "contentType", "image/jpeg", "sizeBytes", 1024)));
        MockHttpServletResponse created = mockMvc.perform(api.json(post("/api/v1/moments"), body)
                .header("Authorization", "Bearer " + owner.accessToken())).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        return api.read(created).get("moment").get("id").asString();
    }

    /** Khoảnh khắc ảnh ở trạng thái READY như sau khi worker xử lý (T-132), không cần chạy ffmpeg. */
    private String readyPhoto(TestUser owner, String audience, List<String> recipientIds) throws Exception {
        String momentId = createPhoto(owner, audience, recipientIds);
        jdbcTemplate.update("UPDATE moments SET status = 'READY', width = 1440, height = 1080, processed_at = now() "
                + "WHERE id = ?::uuid", momentId);
        for (String variant : List.of("DISPLAY", "THUMBNAIL")) {
            jdbcTemplate.update("INSERT INTO moment_media (moment_id, variant, object_key, content_type, size_bytes) "
                    + "VALUES (?::uuid, ?, ?, 'image/jpeg', 100)", momentId, variant,
                    "moments/" + momentId + "/" + variant.toLowerCase() + "-" + UUID.randomUUID() + ".jpg");
        }
        return momentId;
    }

    private TestUser newUser() throws Exception {
        String username = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String accessToken = api.accessTokenForOnboardedUser(uniqueEmail(), username);
        return new TestUser(UUID.fromString(api.read(api.me(accessToken)).get("id").asString()), accessToken);
    }

    private TestUser befriend(TestUser owner) throws Exception {
        TestUser friend = newUser();
        String code = api.read(mockMvc.perform(get("/api/v1/invites/me")
                .header("Authorization", "Bearer " + owner.accessToken())).andReturn().getResponse())
                .get("code").asString();
        mockMvc.perform(post("/api/v1/invites/{code}/accept", code)
                .header("Authorization", "Bearer " + friend.accessToken()).with(randomIp()));
        return friend;
    }
}
