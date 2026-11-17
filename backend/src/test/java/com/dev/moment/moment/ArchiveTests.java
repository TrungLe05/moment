package com.dev.moment.moment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MomentFixtures;
import com.dev.moment.support.MomentFixtures.TestUser;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-151: kho lưu giữ hai tab "Của tôi" và "Được gửi" (US-40, US-41, BR-15, BR-19, API mục 3.4, DB mục 7). */
@IntegrationTest
class ArchiveTests {

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

    // US-40, BR-15: khoảnh khắc lưu giữ của tôi ở lại kho sau 24 giờ; khoảnh khắc chia sẻ, bản nháp không vào kho.
    @Test
    void shouldListMyKeptMomentsNewestFirstIncludingOlderThan24Hours() throws Exception {
        TestUser me = fixtures.newUser();
        fixtures.befriend(me);
        String old = fixtures.readyPhotoForAll(me);
        fixtures.keep(old);
        fixtures.shiftIntoPast(old, Duration.ofDays(40));
        String recent = fixtures.readyPhotoForAll(me);
        fixtures.keep(recent);
        fixtures.readyPhotoForAll(me);
        String keptDraft = fixtures.draftPhoto(me, "ALL", List.of());
        fixtures.keep(keptDraft);

        JsonNode page = archive(me, "?scope=mine");

        assertThat(ids(page)).containsExactly(recent, old);
        assertThat(page.get("nextCursor").isNull()).isTrue();
        JsonNode first = page.get("items").get(0);
        assertThat(first.get("isMine").asBoolean()).isTrue();
        assertThat(first.get("mode").asString()).isEqualTo("KEPT");
        assertThat(first.get("media").get("thumbnailUrl").asString()).startsWith("http");
        assertThat(first.get("ownerView").get("recipientCount").asInt()).isEqualTo(1);
    }

    // US-41, BR-19: chỉ khoảnh khắc lưu giữ gửi cho tôi từ người đang là bạn; không có khoảnh khắc của chính tôi.
    @Test
    void shouldListKeptMomentsFriendsSentMe() throws Exception {
        TestUser me = fixtures.newUser();
        TestUser friend = fixtures.befriend(me);
        TestUser friendOfFriend = fixtures.befriend(friend);
        String sentToMe = fixtures.readyPhotoForAll(friend);
        fixtures.keep(sentToMe);
        fixtures.shiftIntoPast(sentToMe, Duration.ofDays(3));
        String sentToOther = fixtures.readyPhoto(friend, "SELECTED", List.of(friendOfFriend.id().toString()));
        fixtures.keep(sentToOther);
        fixtures.readyPhotoForAll(friend);
        String mine = fixtures.readyPhotoForAll(me);
        fixtures.keep(mine);

        JsonNode page = archive(me, "?scope=received");

        assertThat(ids(page)).containsExactly(sentToMe);
        JsonNode item = page.get("items").get(0);
        assertThat(item.get("isMine").asBoolean()).isFalse();
        assertThat(item.get("owner").get("id").asString()).isEqualTo(friend.id().toString());
        assertThat(item.get("ownerView").isNull()).isTrue();
    }

    // BR-07, BR-19: hủy kết bạn thì khoảnh khắc lưu giữ của bạn đó biến khỏi tab "Được gửi".
    @Test
    void shouldDropReceivedMomentsAfterUnfriending() throws Exception {
        TestUser me = fixtures.newUser();
        TestUser friend = fixtures.befriend(me);
        String kept = fixtures.readyPhotoForAll(friend);
        fixtures.keep(kept);
        assertThat(ids(archive(me, "?scope=received"))).containsExactly(kept);

        MockHttpServletResponse unfriended = fixtures.as(delete("/api/v1/friends/{id}", friend.id()), me);
        assertThat(unfriended.getStatus()).isEqualTo(204);

        assertThat(ids(archive(me, "?scope=received"))).isEmpty();
        // Khoảnh khắc vẫn ở kho của chủ.
        assertThat(ids(archive(friend, "?scope=mine"))).containsExactly(kept);
    }

    // BR-12: người nhận chốt lúc đăng; kết bạn sau không thấy khoảnh khắc lưu giữ cũ.
    @Test
    void shouldNotShowKeptMomentsPostedBeforeWeBecameFriends() throws Exception {
        TestUser friend = fixtures.newUser();
        fixtures.befriend(friend);
        String earlier = fixtures.readyPhotoForAll(friend);
        fixtures.keep(earlier);
        TestUser me = fixtures.befriend(friend);

        assertThat(ids(archive(me, "?scope=received"))).isEmpty();
    }

    @Test
    void shouldPageReceivedMomentsWithCursorWithoutGapsOrDuplicates() throws Exception {
        TestUser me = fixtures.newUser();
        TestUser friend = fixtures.befriend(me);
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String kept = fixtures.readyPhotoForAll(friend);
            fixtures.keep(kept);
            created.add(kept);
        }
        fixtures.shiftIntoPast(created.get(0), Duration.ofDays(2));
        fixtures.shiftIntoPast(created.get(1), Duration.ofDays(2));

        List<String> seen = new ArrayList<>();
        String cursor = "";
        int pages = 0;
        do {
            JsonNode page = archive(me, "?scope=received&limit=2" + cursor);
            seen.addAll(ids(page));
            pages++;
            cursor = page.get("nextCursor").isNull() ? null : "&cursor=" + page.get("nextCursor").asString();
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).containsExactlyInAnyOrderElementsOf(created).doesNotHaveDuplicates();
    }

    @Test
    void shouldReturnThirtyItemsByDefault() throws Exception {
        TestUser me = fixtures.newUser();
        fixtures.befriend(me);
        for (int i = 0; i < 31; i++) {
            fixtures.keep(fixtures.readyPhotoForAll(me));
        }

        JsonNode page = archive(me, "?scope=mine");

        assertThat(page.get("items")).hasSize(30);
        assertThat(page.get("nextCursor").isNull()).isFalse();
    }

    @Test
    void shouldRejectInvalidScopeLimitOrCursor() throws Exception {
        TestUser me = fixtures.newUser();

        for (String query : List.of("", "?scope=all", "?scope=MINE", "?scope=mine&limit=0", "?scope=mine&limit=61",
                "?scope=received&cursor=not-a-cursor")) {
            MockHttpServletResponse response = fixtures.as(get("/api/v1/archive" + query), me);
            assertThat(response.getStatus()).as(query).isEqualTo(400);
            assertThat(api.read(response).get("code").asString()).as(query).isEqualTo("VALIDATION_FAILED");
        }
    }

    @Test
    void shouldReturnEmptyArchiveForUserWithoutFriends() throws Exception {
        TestUser me = fixtures.newUser();

        for (String scope : List.of("mine", "received")) {
            JsonNode page = archive(me, "?scope=" + scope);
            assertThat(page.get("items")).as(scope).isEmpty();
            assertThat(page.get("nextCursor").isNull()).as(scope).isTrue();
        }
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/archive?scope=mine")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    private JsonNode archive(TestUser viewer, String query) throws Exception {
        MockHttpServletResponse response = fixtures.as(get("/api/v1/archive" + query), viewer);
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return api.read(response);
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asString()));
        return ids;
    }
}
