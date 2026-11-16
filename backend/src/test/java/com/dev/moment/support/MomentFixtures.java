package com.dev.moment.support;

import static com.dev.moment.support.ApiTestClient.randomIp;
import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Dựng người dùng, bạn bè và khoảnh khắc cho test xem khoảnh khắc (chi tiết, feed) mà không cần chạy ffmpeg. */
public class MomentFixtures {

    public record TestUser(UUID id, String accessToken) {
    }

    private final MockMvc mockMvc;
    private final ApiTestClient api;
    private final JdbcTemplate jdbcTemplate;

    public MomentFixtures(MockMvc mockMvc, ApiTestClient api, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.api = api;
        this.jdbcTemplate = jdbcTemplate;
    }

    public TestUser newUser() throws Exception {
        String username = "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String accessToken = api.accessTokenForOnboardedUser(uniqueEmail(), username);
        return new TestUser(UUID.fromString(api.read(api.me(accessToken)).get("id").asString()), accessToken);
    }

    /** Một người bạn mới của owner qua mã mời. */
    public TestUser befriend(TestUser owner) throws Exception {
        TestUser friend = newUser();
        befriend(owner, friend);
        return friend;
    }

    public void befriend(TestUser owner, TestUser friend) throws Exception {
        String code = api.read(as(get("/api/v1/invites/me"), owner)).get("code").asString();
        MockHttpServletResponse accepted = mockMvc.perform(post("/api/v1/invites/{code}/accept", code)
                .header("Authorization", "Bearer " + friend.accessToken()).with(randomIp())).andReturn().getResponse();
        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(200);
    }

    public MockHttpServletResponse as(MockHttpServletRequestBuilder request, TestUser user) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + user.accessToken())).andReturn()
                .getResponse();
    }

    /** Bản nháp ảnh tạo qua API (file chưa tải lên). */
    public String draftPhoto(TestUser owner, String audience, List<String> recipientIds) throws Exception {
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
        MockHttpServletResponse created = as(api.json(post("/api/v1/moments"), body), owner);
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        return api.read(created).get("moment").get("id").asString();
    }

    /** Khoảnh khắc ảnh ở trạng thái READY như sau khi worker xử lý (T-132). */
    public String readyPhoto(TestUser owner, String audience, List<String> recipientIds) throws Exception {
        String momentId = draftPhoto(owner, audience, recipientIds);
        jdbcTemplate.update("UPDATE moments SET status = 'READY', width = 1440, height = 1080, processed_at = now() "
                + "WHERE id = ?::uuid", momentId);
        for (String variant : List.of("DISPLAY", "THUMBNAIL")) {
            jdbcTemplate.update("INSERT INTO moment_media (moment_id, variant, object_key, content_type, size_bytes) "
                    + "VALUES (?::uuid, ?, ?, 'image/jpeg', 100)", momentId, variant,
                    "moments/" + momentId + "/" + variant.toLowerCase() + "-" + UUID.randomUUID() + ".jpg");
        }
        return momentId;
    }

    public String readyPhotoForAll(TestUser owner) throws Exception {
        return readyPhoto(owner, "ALL", List.of());
    }

    public void keep(String momentId) {
        jdbcTemplate.update("UPDATE moments SET mode = 'KEPT', kept_at = created_at WHERE id = ?::uuid", momentId);
    }

    /**
     * Lùi created_at, expires_at, kept_at thay vì tua đồng hồ: tua 24 giờ thì access token (15 phút) của người xem
     * hết hạn.
     */
    public void shiftIntoPast(String momentId, Duration age) {
        String interval = age.toSeconds() + " seconds";
        jdbcTemplate.update("UPDATE moments SET created_at = created_at - ?::interval, "
                + "expires_at = expires_at - ?::interval, kept_at = kept_at - ?::interval WHERE id = ?::uuid",
                interval, interval, interval, momentId);
        jdbcTemplate.update("UPDATE moment_recipients SET created_at = created_at - ?::interval WHERE moment_id = ?::uuid",
                interval, momentId);
    }
}
