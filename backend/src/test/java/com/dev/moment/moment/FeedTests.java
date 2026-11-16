package com.dev.moment.moment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.dev.moment.IntegrationTest;
import com.dev.moment.moment.MomentFixtures.TestUser;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T-142: feed 24 giờ (US-30, BR-13, BR-19, API mục 3.4, DB mục 7). */
@IntegrationTest
class FeedTests {

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

    @Test
    void shouldShowMyAndFriendsMomentsNewestFirst() throws Exception {
        TestUser me = fixtures.newUser();
        TestUser friend = fixtures.befriend(me);
        String mine = fixtures.readyPhotoForAll(me);
        String fromFriend = fixtures.readyPhotoForAll(friend);
        fixtures.shiftIntoPast(mine, Duration.ofHours(2));
        fixtures.shiftIntoPast(fromFriend, Duration.ofHours(1));

        JsonNode page = feed(me, "");

        assertThat(ids(page)).containsExactly(fromFriend, mine);
        assertThat(page.get("nextCursor").isNull()).isTrue();
        JsonNode first = page.get("items").get(0);
        assertThat(first.get("isMine").asBoolean()).isFalse();
        assertThat(first.get("ownerView").isNull()).isTrue();
        assertThat(first.get("owner").get("id").asString()).isEqualTo(friend.id().toString());
        assertThat(first.get("media").get("displayUrl").asString()).startsWith("http");
        JsonNode second = page.get("items").get(1);
        assertThat(second.get("isMine").asBoolean()).isTrue();
        assertThat(second.get("ownerView").get("recipientCount").asInt()).isEqualTo(1);
    }

    // BR-13, BR-19: chỉ khoảnh khắc đã xử lý xong, gửi cho tôi, từ người đang là bạn, còn trong 24 giờ.
    @Test
    void shouldLeaveOutMomentsIAmNotAllowedToSee() throws Exception {
        TestUser me = fixtures.newUser();
        TestUser friend = fixtures.befriend(me);
        TestUser otherFriendOfFriend = fixtures.befriend(friend);
        String visible = fixtures.readyPhotoForAll(friend);
        fixtures.readyPhoto(friend, "SELECTED", List.of(otherFriendOfFriend.id().toString()));
        fixtures.draftPhoto(friend, "ALL", List.of());
        String expired = fixtures.readyPhotoForAll(friend);
        fixtures.shiftIntoPast(expired, Duration.ofHours(24));
        fixtures.readyPhotoForAll(otherFriendOfFriend);

        assertThat(ids(feed(me, ""))).containsExactly(visible);
    }

    @Test
    void shouldDropFriendsMomentsAfterUnfriending() throws Exception {
        TestUser me = fixtures.newUser();
        TestUser friend = fixtures.befriend(me);
        fixtures.readyPhotoForAll(friend);

        MockHttpServletResponse unfriended = fixtures.as(delete("/api/v1/friends/{id}", friend.id()), me);
        assertThat(unfriended.getStatus()).isEqualTo(204);

        assertThat(ids(feed(me, ""))).isEmpty();
    }

    // BR-15: khoảnh khắc lưu giữ rời feed sau 24 giờ (vẫn nằm trong kho).
    @Test
    void shouldDropKeptMomentsFromFeedAfter24Hours() throws Exception {
        TestUser me = fixtures.newUser();
        TestUser friend = fixtures.befriend(me);
        String kept = fixtures.readyPhotoForAll(friend);
        fixtures.keep(kept);
        fixtures.shiftIntoPast(kept, Duration.ofHours(24));

        assertThat(ids(feed(me, ""))).isEmpty();
    }

    @Test
    void shouldPageWithOpaqueCursorWithoutGapsOrDuplicates() throws Exception {
        TestUser me = fixtures.newUser();
        fixtures.befriend(me);
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(fixtures.readyPhotoForAll(me));
        }
        // Hai khoảnh khắc cùng created_at: thứ tự phụ theo id, con trỏ vẫn không bỏ sót.
        fixtures.shiftIntoPast(created.get(0), Duration.ofMinutes(10));
        fixtures.shiftIntoPast(created.get(1), Duration.ofMinutes(10));
        fixtures.shiftIntoPast(created.get(2), Duration.ofMinutes(5));

        List<String> seen = new ArrayList<>();
        String cursor = "";
        int pages = 0;
        do {
            JsonNode page = feed(me, "?limit=2" + cursor);
            seen.addAll(ids(page));
            pages++;
            cursor = page.get("nextCursor").isNull() ? null : "&cursor=" + page.get("nextCursor").asString();
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).containsExactlyInAnyOrderElementsOf(created).doesNotHaveDuplicates();
        List<String> sameTime = new ArrayList<>(List.of(created.get(0), created.get(1)));
        // PostgreSQL so uuid theo byte không dấu, trùng thứ tự chuỗi hex chữ thường.
        sameTime.sort(Comparator.reverseOrder());
        assertThat(seen.subList(3, 5)).containsExactlyElementsOf(sameTime);
    }

    @Test
    void shouldReturnTenItemsByDefault() throws Exception {
        TestUser me = fixtures.newUser();
        fixtures.befriend(me);
        for (int i = 0; i < 11; i++) {
            fixtures.readyPhotoForAll(me);
        }

        JsonNode page = feed(me, "");

        assertThat(page.get("items")).hasSize(10);
        assertThat(page.get("nextCursor").isNull()).isFalse();
    }

    @Test
    void shouldRejectLimitOutsideOneToThirty() throws Exception {
        TestUser me = fixtures.newUser();

        for (String limit : List.of("0", "31", "abc")) {
            MockHttpServletResponse response = fixtures.as(get("/api/v1/feed?limit=" + limit), me);
            assertThat(response.getStatus()).as(limit).isEqualTo(400);
            assertThat(api.read(response).get("code").asString()).isEqualTo("VALIDATION_FAILED");
        }
    }

    @Test
    void shouldRejectCursorTheServerDidNotIssue() throws Exception {
        TestUser me = fixtures.newUser();

        for (String cursor : List.of("not-a-cursor", "bm90IGEgY3Vyc29y")) {
            MockHttpServletResponse response = fixtures.as(get("/api/v1/feed?cursor=" + cursor), me);
            assertThat(response.getStatus()).as(cursor).isEqualTo(400);
            assertThat(api.read(response).get("code").asString()).isEqualTo("VALIDATION_FAILED");
        }
    }

    @Test
    void shouldReturnEmptyFeedForNewUser() throws Exception {
        JsonNode page = feed(fixtures.newUser(), "");

        assertThat(page.get("items")).isEmpty();
        assertThat(page.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/feed")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    private JsonNode feed(TestUser viewer, String query) throws Exception {
        MockHttpServletResponse response = fixtures.as(get("/api/v1/feed" + query), viewer);
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return api.read(response);
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asString()));
        return ids;
    }
}
