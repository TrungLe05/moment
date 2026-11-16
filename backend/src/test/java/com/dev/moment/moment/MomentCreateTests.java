package com.dev.moment.moment;

import static com.dev.moment.support.ApiTestClient.randomIp;
import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.RecordingEmailSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-131: tạo khoảnh khắc, cấp presigned URL, complete, retry (API mục 3.4, BR-08 → BR-13). */
@IntegrationTest
class MomentCreateTests {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RecordingEmailSender emails;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, jsonMapper, emails);
    }

    // --- Tạo bản nháp ---

    @Test
    void shouldCreateDraftPhotoForAllFriendsWithUploadUrls() throws Exception {
        TestUser owner = newUser();
        TestUser friend = befriend(owner);

        MockHttpServletResponse response = create(owner, photoBody());

        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode body = api.read(response);
        JsonNode moment = body.get("moment");
        assertThat(moment.get("status").asString()).isEqualTo("DRAFT");
        assertThat(moment.get("isMine").asBoolean()).isTrue();
        Instant createdAt = Instant.parse(moment.get("createdAt").asString());
        assertThat(Instant.parse(moment.get("expiresAt").asString())).isEqualTo(createdAt.plus(Duration.ofHours(24)));
        assertThat(moment.get("ownerView").get("recipientCount").asInt()).isEqualTo(1);
        assertThat(body.get("uploads")).hasSize(2);
        for (JsonNode upload : body.get("uploads")) {
            assertThat(upload.get("method").asString()).isEqualTo("PUT");
            assertThat(upload.get("url").asString()).startsWith("http");
            assertThat(upload.get("headers").has("Content-Type")).isTrue();
        }
        assertThat(recipients(moment.get("id").asString())).containsExactly(friend.id());
    }

    @Test
    void shouldSetKeptAtWhenCreatedInKeptMode() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        Map<String, Object> body = photoBody();
        body.put("mode", "KEPT");

        JsonNode moment = api.read(create(owner, body)).get("moment");

        assertThat(moment.get("mode").asString()).isEqualTo("KEPT");
        assertThat(moment.get("keptAt").asString()).isEqualTo(moment.get("createdAt").asString());
    }

    @Test
    void shouldRejectMomentWhenOwnerHasNoFriends() throws Exception {
        MockHttpServletResponse response = create(newUser(), photoBody());

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(code(response)).isEqualTo("NO_FRIENDS");
    }

    @Test
    void shouldSendOnlyToSelectedFriends() throws Exception {
        TestUser owner = newUser();
        TestUser chosen = befriend(owner);
        befriend(owner);
        Map<String, Object> body = photoBody();
        body.put("audience", "SELECTED");
        body.put("recipientIds", List.of(chosen.id().toString()));

        JsonNode moment = api.read(create(owner, body)).get("moment");

        assertThat(recipients(moment.get("id").asString())).containsExactly(chosen.id());
    }

    @Test
    void shouldRejectSelectedRecipientsWhoAreNotFriends() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        TestUser stranger = newUser();
        Map<String, Object> body = photoBody();
        body.put("audience", "SELECTED");
        body.put("recipientIds", List.of(stranger.id().toString()));

        MockHttpServletResponse response = create(owner, body);

        assertThat(response.getStatus()).isEqualTo(422);
        JsonNode problem = api.read(response);
        assertThat(problem.get("code").asString()).isEqualTo("NOT_FRIENDS");
        assertThat(problem.get("errors").get(0).get("field").asString()).isEqualTo("recipientIds");
    }

    @Test
    void shouldRequireAtLeastOneSelectedRecipient() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        Map<String, Object> body = photoBody();
        body.put("audience", "SELECTED");
        body.put("recipientIds", List.of());

        assertThat(code(create(owner, body))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void shouldRequireOriginalAndComposedFilesForPhoto() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        Map<String, Object> body = photoBody();
        body.put("files", List.of(file("ORIGINAL", "image/jpeg", 1000)));

        assertThat(code(create(owner, body))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void shouldRejectUnsupportedFileType() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        Map<String, Object> body = photoBody();
        body.put("files", List.of(file("ORIGINAL", "image/gif", 1000), file("COMPOSED", "image/webp", 500)));

        MockHttpServletResponse response = create(owner, body);

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(code(response)).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void shouldRejectPhotoLargerThan15Megabytes() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        Map<String, Object> body = photoBody();
        body.put("files", List.of(file("ORIGINAL", "image/png", 15L * 1024 * 1024 + 1), file("COMPOSED", "image/webp", 500)));

        MockHttpServletResponse response = create(owner, body);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(code(response)).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void shouldAcceptVideoUpTo50MegabytesWithTrimStartForUploads() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        Map<String, Object> body = videoBody("UPLOAD", 50L * 1024 * 1024);
        body.put("trimStartMs", 12000);

        assertThat(create(owner, body).getStatus()).isEqualTo(201);
    }

    @Test
    void shouldRejectTrimStartForCameraVideo() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        Map<String, Object> body = videoBody("CAMERA", 1000);
        body.put("trimStartMs", 1000);

        assertThat(code(create(owner, body))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void shouldLimitCaptionTo80VisibleCharactersAnd3Lines() throws Exception {
        TestUser owner = newUser();
        befriend(owner);

        Map<String, Object> emojiCaption = photoBody();
        emojiCaption.put("caption", "🔥".repeat(80));
        assertThat(create(owner, emojiCaption).getStatus()).isEqualTo(201);

        Map<String, Object> tooLong = photoBody();
        tooLong.put("caption", "ệ".repeat(81));
        assertThat(code(create(owner, tooLong))).isEqualTo("VALIDATION_FAILED");

        Map<String, Object> tooManyLines = photoBody();
        tooManyLines.put("caption", "a\nb\nc\nd");
        assertThat(code(create(owner, tooManyLines))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void shouldValidateDecorationLayers() throws Exception {
        TestUser owner = newUser();
        befriend(owner);

        Map<String, Object> tooMany = photoBody();
        List<Map<String, Object>> layers = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            layers.add(layer("s" + i, "sticker", "hi", 0.5));
        }
        tooMany.put("layers", layers);
        assertThat(code(create(owner, tooMany))).isEqualTo("VALIDATION_FAILED");

        Map<String, Object> longSticker = photoBody();
        longSticker.put("layers", List.of(layer("s1", "sticker", "x".repeat(41), 0.5)));
        assertThat(code(create(owner, longSticker))).isEqualTo("VALIDATION_FAILED");

        Map<String, Object> outOfFrame = photoBody();
        outOfFrame.put("layers", List.of(layer("s1", "sticker", "hi", 1.5)));
        assertThat(code(create(owner, outOfFrame))).isEqualTo("VALIDATION_FAILED");

        Map<String, Object> twoCaptions = photoBody();
        twoCaptions.put("layers", List.of(layer("c1", "caption", "a", 0.5), layer("c2", "caption", "b", 0.5)));
        assertThat(code(create(owner, twoCaptions))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void shouldCopyCaptionLayerTextIntoCaption() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        Map<String, Object> body = photoBody();
        body.put("caption", null);
        body.put("layers", List.of(layer("c1", "caption", "trời đẹp quá", 0.5)));

        JsonNode moment = api.read(create(owner, body)).get("moment");

        assertThat(moment.get("caption").asString()).isEqualTo("trời đẹp quá");
    }

    // --- Báo tải xong và xử lý lại ---

    @Test
    void shouldStartProcessingAfterAllFilesAreUploaded() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        JsonNode created = api.read(create(owner, photoBody()));
        String momentId = created.get("moment").get("id").asString();
        uploadAll(created.get("uploads"));

        MockHttpServletResponse response = authed(post("/api/v1/moments/{id}/complete", momentId), owner);

        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(api.read(response).get("status").asString()).isEqualTo("PROCESSING");
        assertThat(jobs(momentId)).containsExactly("PROCESS_MOMENT");
    }

    @Test
    void shouldRejectCompleteWhenAFileIsMissing() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        JsonNode created = api.read(create(owner, photoBody()));
        String momentId = created.get("moment").get("id").asString();
        upload(created.get("uploads").get(0));

        MockHttpServletResponse response = authed(post("/api/v1/moments/{id}/complete", momentId), owner);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("UPLOAD_INCOMPLETE");
        assertThat(jobs(momentId)).isEmpty();
    }

    @Test
    void shouldRejectCompleteTwice() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        JsonNode created = api.read(create(owner, photoBody()));
        String momentId = created.get("moment").get("id").asString();
        uploadAll(created.get("uploads"));
        authed(post("/api/v1/moments/{id}/complete", momentId), owner);

        assertThat(code(authed(post("/api/v1/moments/{id}/complete", momentId), owner))).isEqualTo("MOMENT_NOT_DRAFT");
    }

    @Test
    void shouldHideOtherUsersMomentsFromComplete() throws Exception {
        TestUser owner = newUser();
        TestUser friend = befriend(owner);
        String momentId = api.read(create(owner, photoBody())).get("moment").get("id").asString();

        MockHttpServletResponse response = authed(post("/api/v1/moments/{id}/complete", momentId), friend);

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void shouldRetryOnlyFailedMoments() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        String momentId = api.read(create(owner, photoBody())).get("moment").get("id").asString();

        assertThat(code(authed(post("/api/v1/moments/{id}/retry", momentId), owner))).isEqualTo("MOMENT_NOT_FAILED");

        jdbcTemplate.update("UPDATE moments SET status = 'FAILED', failure_reason = 'test' WHERE id = ?::uuid", momentId);
        MockHttpServletResponse retried = authed(post("/api/v1/moments/{id}/retry", momentId), owner);

        assertThat(retried.getStatus()).isEqualTo(202);
        JsonNode moment = api.read(retried);
        assertThat(moment.get("status").asString()).isEqualTo("PROCESSING");
        assertThat(moment.get("ownerView").get("failureReason").isNull()).isTrue();
        assertThat(jobs(momentId)).containsExactly("PROCESS_MOMENT");
    }

    @Test
    void shouldRateLimitMomentCreationTo60PerHour() throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        for (int i = 0; i < 60; i++) {
            assertThat(create(owner, photoBody()).getStatus()).isEqualTo(201);
        }

        assertThat(code(create(owner, photoBody()))).isEqualTo("RATE_LIMITED");
    }

    // --- Tiện ích ---

    private record TestUser(UUID id, String accessToken) {
    }

    private TestUser newUser() throws Exception {
        String username = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String accessToken = api.accessTokenForOnboardedUser(uniqueEmail(), username);
        return new TestUser(UUID.fromString(api.read(api.me(accessToken)).get("id").asString()), accessToken);
    }

    /** Tạo một người bạn mới của owner qua mã mời. */
    private TestUser befriend(TestUser owner) throws Exception {
        TestUser friend = newUser();
        String code = api.read(authed(get("/api/v1/invites/me"), owner)).get("code").asString();
        mockMvc.perform(post("/api/v1/invites/{code}/accept", code)
                .header("Authorization", "Bearer " + friend.accessToken()).with(randomIp()));
        return friend;
    }

    private static Map<String, Object> photoBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("mediaType", "PHOTO");
        body.put("source", "CAMERA");
        body.put("mode", "SHARED");
        body.put("audience", "ALL");
        body.put("caption", "trời hôm nay đẹp quá");
        body.put("layers", List.of());
        body.put("layersVersion", 1);
        body.put("files", List.of(file("ORIGINAL", "image/jpeg", 2048), file("COMPOSED", "image/webp", 1024)));
        return body;
    }

    private static Map<String, Object> videoBody(String source, long sizeBytes) {
        Map<String, Object> body = photoBody();
        body.put("mediaType", "VIDEO");
        body.put("source", source);
        body.put("files", List.of(file("ORIGINAL", "video/mp4", sizeBytes)));
        return body;
    }

    private static Map<String, Object> file(String variant, String contentType, long sizeBytes) {
        return Map.of("variant", variant, "contentType", contentType, "sizeBytes", sizeBytes);
    }

    private static Map<String, Object> layer(String id, String type, String text, double x) {
        return Map.of("id", id, "type", type, "text", text, "x", x, "y", 0.5, "scale", 1, "rotation", 0,
                "style", "pill-dark");
    }

    private MockHttpServletResponse create(TestUser owner, Map<String, Object> body) throws Exception {
        return mockMvc.perform(api.json(post("/api/v1/moments"), body)
                .header("Authorization", "Bearer " + owner.accessToken())).andReturn().getResponse();
    }

    private MockHttpServletResponse authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, TestUser user)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + user.accessToken()))
                .andReturn().getResponse();
    }

    private String code(MockHttpServletResponse response) throws Exception {
        return api.read(response).get("code").asString();
    }

    private List<UUID> recipients(String momentId) {
        return jdbcTemplate.queryForList(
                "SELECT recipient_id FROM moment_recipients WHERE moment_id = ?::uuid", UUID.class, momentId);
    }

    private List<String> jobs(String momentId) {
        return jdbcTemplate.queryForList(
                "SELECT type FROM media_jobs WHERE moment_id = ?::uuid AND status = 'PENDING'", String.class, momentId);
    }

    private void uploadAll(JsonNode uploads) throws Exception {
        for (JsonNode upload : uploads) {
            upload(upload);
        }
    }

    /** PUT thẳng lên object storage bằng presigned URL, đúng dung lượng đã khai báo trong photoBody. */
    private void upload(JsonNode upload) throws Exception {
        int size = upload.get("variant").asString().equals("ORIGINAL") ? 2048 : 1024;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(upload.get("url").asString()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[size]));
        upload.get("headers").properties().forEach(header -> request.header(header.getKey(), header.getValue().asString()));
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("PUT presigned URL: " + response.body()).isEqualTo(200);
    }
}
