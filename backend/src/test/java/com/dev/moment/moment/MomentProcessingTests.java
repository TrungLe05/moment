package com.dev.moment.moment;

import static com.dev.moment.support.ApiTestClient.randomIp;
import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dev.moment.IntegrationTest;
import com.dev.moment.job.MediaWorker;
import com.dev.moment.media.ObjectStorage;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import com.dev.moment.support.TestMedia;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

/**
 * T-132: worker xử lý khoảnh khắc bằng ffmpeg (DB mục 6, ADR 0003, D-033).
 * Đi trọn luồng thật: tạo qua API, PUT lên RustFS bằng presigned URL, complete, rồi cho worker chạy.
 */
@IntegrationTest
class MomentProcessingTests {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RecordingEmailSender emails;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MediaWorker worker;
    @Autowired
    private ObjectStorage storage;
    @Autowired
    private MutableClock clock;

    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, jsonMapper, emails);
        clock.reset();
        // Việc do các lớp test khác để lại (file giả) không được lẫn vào lượt chạy của worker ở đây.
        jdbcTemplate.update("UPDATE media_jobs SET status = 'DONE' WHERE status IN ('PENDING', 'RUNNING')");
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    // --- Ảnh ---

    @Test
    void shouldCreateDisplayAndThumbnailFromComposedPhoto() throws Exception {
        Posted posted = postPhoto(TestMedia.image(2000, 1500, "jpg"), TestMedia.image(2000, 1500, "webp"), "image/webp");

        worker.drain();

        Map<String, Object> moment = moment(posted.id());
        assertThat(moment.get("status")).isEqualTo("READY");
        assertThat(moment.get("width")).isEqualTo(1440);
        assertThat(moment.get("height")).isEqualTo(1080);
        assertThat(moment.get("processed_at")).isNotNull();
        assertThat(moment.get("failure_reason")).isNull();
        assertThat(variants(posted.id())).containsOnlyKeys("ORIGINAL", "COMPOSED", "DISPLAY", "THUMBNAIL");
        assertFile(posted.id(), "DISPLAY", "image/jpeg", 1440, 1080);
        assertFile(posted.id(), "THUMBNAIL", "image/jpeg", 480, 360);
        assertThat(jobStatuses(posted.id())).containsExactly("DONE");
    }

    @Test
    void shouldNotUpscaleSmallPhotos() throws Exception {
        Posted posted = postPhoto(TestMedia.image(800, 1000, "jpg"), TestMedia.image(800, 1000, "jpg"), "image/jpeg");

        worker.drain();

        assertFile(posted.id(), "DISPLAY", "image/jpeg", 800, 1000);
        assertFile(posted.id(), "THUMBNAIL", "image/jpeg", 384, 480);
    }

    // --- Video ---

    @Test
    void shouldTrimUploadedVideoToFiveSecondsFromStartPoint() throws Exception {
        Posted posted = postVideo(TestMedia.video(1920, 1080, 12, true), "UPLOAD", 3000);
        String originalKey = variants(posted.id()).get("ORIGINAL");

        worker.drain();

        Map<String, Object> moment = moment(posted.id());
        assertThat(moment.get("status")).isEqualTo("READY");
        assertThat((Integer) moment.get("duration_ms")).isCloseTo(5000, within(150));
        assertThat(moment.get("width")).isEqualTo(1280);
        assertThat(moment.get("height")).isEqualTo(720);
        assertFile(posted.id(), "VIDEO", "video/mp4", 1280, 720);
        assertFile(posted.id(), "THUMBNAIL", "image/jpeg", 480, 270);
        // Video gốc xóa sau khi xử lý xong (DB mục 6), qua việc DELETE_OBJECTS.
        assertThat(variants(posted.id())).containsOnlyKeys("VIDEO", "THUMBNAIL");
        assertThat(storage.find(originalKey)).isEmpty();
    }

    @Test
    void shouldScalePortraitVideoByShortSideAndKeepShortClips() throws Exception {
        Posted posted = postVideo(TestMedia.video(1080, 1920, 3, false), "CAMERA", null);

        worker.drain();

        Map<String, Object> moment = moment(posted.id());
        assertThat(moment.get("status")).isEqualTo("READY");
        assertThat((Integer) moment.get("duration_ms")).isCloseTo(3000, within(150));
        assertFile(posted.id(), "VIDEO", "video/mp4", 720, 1280);
        assertFile(posted.id(), "THUMBNAIL", "image/jpeg", 270, 480);
    }

    @Test
    void shouldMoveStartPointBackSoClipStaysFiveSeconds() throws Exception {
        Posted posted = postVideo(TestMedia.video(320, 240, 8, false), "UPLOAD", 7000);

        worker.drain();

        assertThat((Integer) moment(posted.id()).get("duration_ms")).isCloseTo(5000, within(150));
    }

    @Test
    void shouldRejectVideosLongerThan60Seconds() throws Exception {
        Posted posted = postVideo(TestMedia.video(64, 64, 61, false), "UPLOAD", 0);

        worker.drain();

        assertFailedWithoutRetry(posted.id(), "VIDEO_TOO_LONG");
    }

    // --- Lỗi ---

    @Test
    void shouldFailWithoutRetryWhenComposedPhotoIsUnreadable() throws Exception {
        byte[] garbage = "không phải ảnh".repeat(50).getBytes(StandardCharsets.UTF_8);
        Posted posted = postPhoto(TestMedia.image(320, 240, "jpg"), garbage, "image/webp");

        worker.drain();

        assertFailedWithoutRetry(posted.id(), "MEDIA_UNREADABLE");
        assertThat(variants(posted.id())).containsOnlyKeys("ORIGINAL", "COMPOSED");
    }

    // Playlist HLS đội lốt mp4 từng khiến ffmpeg đọc file cục bộ hoặc gọi URL ngoài. ffmpeg bản mới tự chặn,
    // worker vẫn ép đúng demuxer và chỉ cho giao thức file để không phụ thuộc phiên bản cài trong container.
    @Test
    void shouldRejectPlaylistDisguisedAsVideo() throws Exception {
        byte[] playlist = """
                #EXTM3U
                #EXT-X-TARGETDURATION:5
                #EXTINF:5.0,
                http://127.0.0.1:1/leak
                #EXT-X-ENDLIST
                """.getBytes(StandardCharsets.UTF_8);
        Posted posted = postVideo(playlist, "UPLOAD", null);

        worker.drain();

        assertFailedWithoutRetry(posted.id(), "MEDIA_UNREADABLE");
    }

    @Test
    void shouldRetryTemporaryErrorsWithGrowingDelayThenFail() throws Exception {
        Posted posted = postPhoto(TestMedia.image(320, 240, "jpg"), TestMedia.image(320, 240, "jpg"), "image/jpeg");
        // File biến mất khỏi storage sau khi complete: lỗi phía storage, không phải lỗi của file.
        storage.deleteAll(List.of(variants(posted.id()).get("COMPOSED")));

        assertThat(worker.drain()).isEqualTo(1);
        Map<String, Object> job = job(posted.id());
        assertThat(job.get("status")).isEqualTo("PENDING");
        assertThat(job.get("attempts")).isEqualTo(1);
        assertThat(job.get("last_error")).isNotNull();
        assertThat(moment(posted.id()).get("status")).isEqualTo("PROCESSING");

        // Chưa tới giờ chạy lại thì worker không lấy.
        assertThat(worker.drain()).isZero();
        clock.advance(Duration.ofSeconds(15));
        assertThat(worker.drain()).isEqualTo(1);
        assertThat(job(posted.id()).get("attempts")).isEqualTo(2);

        clock.advance(Duration.ofSeconds(59));
        assertThat(worker.drain()).isZero();
        clock.advance(Duration.ofSeconds(1));
        assertThat(worker.drain()).isEqualTo(1);

        job = job(posted.id());
        assertThat(job.get("status")).isEqualTo("FAILED");
        assertThat(job.get("attempts")).isEqualTo(3);
        Map<String, Object> moment = moment(posted.id());
        assertThat(moment.get("status")).isEqualTo("FAILED");
        assertThat(moment.get("failure_reason")).isEqualTo("PROCESSING_ERROR");
    }

    @Test
    void shouldBecomeReadyWhenOwnerRetriesAfterFailure() throws Exception {
        byte[] composed = TestMedia.image(320, 240, "jpg");
        Posted posted = postPhoto(TestMedia.image(320, 240, "jpg"), composed, "image/jpeg");
        jdbcTemplate.update("UPDATE moments SET status = 'FAILED', failure_reason = 'PROCESSING_ERROR' WHERE id = ?::uuid",
                posted.id());
        jdbcTemplate.update("UPDATE media_jobs SET status = 'FAILED' WHERE moment_id = ?::uuid", posted.id());

        MockHttpServletResponse retried = mockMvc.perform(post("/api/v1/moments/{id}/retry", posted.id())
                .header("Authorization", "Bearer " + posted.ownerToken())).andReturn().getResponse();
        assertThat(retried.getStatus()).isEqualTo(202);
        worker.drain();

        assertThat(moment(posted.id()).get("status")).isEqualTo("READY");
        assertThat(moment(posted.id()).get("failure_reason")).isNull();
    }

    @Test
    void shouldSkipJobWhenMomentWasDeletedMeanwhile() throws Exception {
        Posted posted = postPhoto(TestMedia.image(320, 240, "jpg"), TestMedia.image(320, 240, "jpg"), "image/jpeg");
        jdbcTemplate.update("DELETE FROM moments WHERE id = ?::uuid", posted.id());

        worker.drain();

        assertThat(jobStatuses(posted.id())).containsExactly("DONE");
    }

    @Test
    void shouldNotRevivePhotoThatExpiredWhileProcessing() throws Exception {
        Posted posted = postPhoto(TestMedia.image(320, 240, "jpg"), TestMedia.image(320, 240, "jpg"), "image/jpeg");
        jdbcTemplate.update("UPDATE moments SET status = 'EXPIRED' WHERE id = ?::uuid", posted.id());

        worker.drain();

        assertThat(moment(posted.id()).get("status")).isEqualTo("EXPIRED");
        assertThat(variants(posted.id())).containsOnlyKeys("ORIGINAL", "COMPOSED");
        assertThat(jobStatuses(posted.id())).containsExactly("DONE");
    }

    // --- Tiện ích ---

    private record Posted(String id, String ownerToken) {
    }

    private record TestUser(UUID id, String accessToken) {
    }

    private Posted postPhoto(byte[] original, byte[] composed, String composedType) throws Exception {
        Map<String, Object> body = baseBody("PHOTO", "CAMERA");
        body.put("files", List.of(file("ORIGINAL", "image/jpeg", original.length),
                file("COMPOSED", composedType, composed.length)));
        return publish(body, Map.of("ORIGINAL", original, "COMPOSED", composed));
    }

    private Posted postVideo(byte[] video, String source, Integer trimStartMs) throws Exception {
        Map<String, Object> body = baseBody("VIDEO", source);
        body.put("files", List.of(file("ORIGINAL", "video/mp4", video.length)));
        body.put("trimStartMs", trimStartMs);
        return publish(body, Map.of("ORIGINAL", video));
    }

    /** Tạo qua API, PUT từng file bằng presigned URL rồi báo tải xong. */
    private Posted publish(Map<String, Object> body, Map<String, byte[]> contents) throws Exception {
        TestUser owner = newUser();
        befriend(owner);
        MockHttpServletResponse created = mockMvc.perform(api.json(post("/api/v1/moments"), body)
                .header("Authorization", "Bearer " + owner.accessToken())).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        JsonNode response = api.read(created);
        for (JsonNode upload : response.get("uploads")) {
            put(upload, contents.get(upload.get("variant").asString()));
        }
        String id = response.get("moment").get("id").asString();
        MockHttpServletResponse completed = mockMvc.perform(post("/api/v1/moments/{id}/complete", id)
                .header("Authorization", "Bearer " + owner.accessToken())).andReturn().getResponse();
        assertThat(completed.getStatus()).as(completed.getContentAsString()).isEqualTo(202);
        return new Posted(id, owner.accessToken());
    }

    private static Map<String, Object> baseBody(String mediaType, String source) {
        Map<String, Object> body = new HashMap<>();
        body.put("mediaType", mediaType);
        body.put("source", source);
        body.put("mode", "SHARED");
        body.put("audience", "ALL");
        body.put("layers", List.of());
        body.put("layersVersion", 1);
        return body;
    }

    private static Map<String, Object> file(String variant, String contentType, long sizeBytes) {
        return Map.of("variant", variant, "contentType", contentType, "sizeBytes", sizeBytes);
    }

    private static void put(JsonNode upload, byte[] content) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(upload.get("url").asString()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        upload.get("headers").properties().forEach(header -> request.header(header.getKey(), header.getValue().asString()));
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("PUT presigned URL: " + response.body()).isEqualTo(200);
    }

    private TestUser newUser() throws Exception {
        String username = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String accessToken = api.accessTokenForOnboardedUser(uniqueEmail(), username);
        return new TestUser(UUID.fromString(api.read(api.me(accessToken)).get("id").asString()), accessToken);
    }

    private void befriend(TestUser owner) throws Exception {
        TestUser friend = newUser();
        String code = api.read(mockMvc.perform(get("/api/v1/invites/me")
                .header("Authorization", "Bearer " + owner.accessToken())).andReturn().getResponse())
                .get("code").asString();
        mockMvc.perform(post("/api/v1/invites/{code}/accept", code)
                .header("Authorization", "Bearer " + friend.accessToken()).with(randomIp()));
    }

    private Map<String, Object> moment(String id) {
        return jdbcTemplate.queryForMap("SELECT * FROM moments WHERE id = ?::uuid", id);
    }

    /** Biến thể → khóa file. */
    private Map<String, String> variants(String momentId) {
        Map<String, String> keys = new HashMap<>();
        jdbcTemplate.query("SELECT variant, object_key FROM moment_media WHERE moment_id = ?::uuid",
                row -> {
                    keys.put(row.getString("variant"), row.getString("object_key"));
                }, momentId);
        return keys;
    }

    private void assertFile(String momentId, String variant, String contentType, int width, int height) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM moment_media WHERE moment_id = ?::uuid AND variant = ?", momentId, variant);
        assertThat(row.get("content_type")).isEqualTo(contentType);
        assertThat(row.get("width")).isEqualTo(width);
        assertThat(row.get("height")).isEqualTo(height);
        String key = (String) row.get("object_key");
        assertThat(key).matches("moments/" + momentId + "/" + variant.toLowerCase() + "-[0-9a-f-]{36}\\.(jpg|mp4)");
        ObjectStorage.StoredObject stored = storage.find(key).orElseThrow();
        assertThat(stored.sizeBytes()).isEqualTo(row.get("size_bytes"));
        assertThat(stored.contentType()).isEqualTo(contentType);
    }

    private void assertFailedWithoutRetry(String momentId, String reason) {
        Map<String, Object> moment = moment(momentId);
        assertThat(moment.get("status")).isEqualTo("FAILED");
        assertThat(moment.get("failure_reason")).isEqualTo(reason);
        Map<String, Object> job = job(momentId);
        assertThat(job.get("status")).isEqualTo("FAILED");
        assertThat(job.get("attempts")).isEqualTo(1);
    }

    private Map<String, Object> job(String momentId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM media_jobs WHERE moment_id = ?::uuid AND type = 'PROCESS_MOMENT' ORDER BY id DESC LIMIT 1",
                momentId);
    }

    private List<String> jobStatuses(String momentId) {
        return jdbcTemplate.queryForList(
                "SELECT status FROM media_jobs WHERE moment_id = ?::uuid AND type = 'PROCESS_MOMENT'", String.class,
                momentId);
    }
}
