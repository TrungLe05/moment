package com.dev.moment.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.MomentFixtures;
import com.dev.moment.support.MomentFixtures.TestUser;
import com.dev.moment.support.MutableClock;
import com.dev.moment.support.RecordingEmailSender;
import java.time.Duration;
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

/** T-143: reaction (US-32, US-33, BR-21 → BR-24, D-028, API mục 3.5). */
@IntegrationTest
class ReactionTests {

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

    // --- Thả, đổi, gỡ ---

    @Test
    void shouldSetMyReactionAndShowItOnTheMoment() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);

        MockHttpServletResponse response = react(momentId, friend, "🔥");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(api.read(response).get("emoji").asString()).isEqualTo("🔥");
        assertThat(detail(momentId, friend).get("myReaction").asString()).isEqualTo("🔥");
    }

    // BR-21: mỗi người tối đa một reaction; thả emoji khác thì thay emoji cũ.
    @Test
    void shouldReplaceReactionWithTheNewEmoji() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);

        react(momentId, friend, "🔥");
        react(momentId, friend, "😂");

        assertThat(detail(momentId, friend).get("myReaction").asString()).isEqualTo("😂");
        assertThat(reactionRows(momentId)).isEqualTo(1);
    }

    @Test
    void shouldRemoveReactionAndAllowRemovingTwice() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);
        react(momentId, friend, "😮");

        assertThat(unreact(momentId, friend).getStatus()).isEqualTo(204);
        assertThat(unreact(momentId, friend).getStatus()).isEqualTo(204);

        assertThat(detail(momentId, friend).get("myReaction").isNull()).isTrue();
    }

    // D-028: ❤ không có U+FE0F được chuẩn hóa thành ❤️; emoji khác kèm U+FE0F cũng nhận.
    @Test
    void shouldNormalizeEmojiVariationSelectors() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);

        assertThat(api.read(react(momentId, friend, "❤")).get("emoji").asString()).isEqualTo("❤️");
        assertThat(api.read(react(momentId, friend, "😢️")).get("emoji").asString())
                .isEqualTo("😢");
    }

    // BR-21: bộ emoji cố định.
    @Test
    void shouldRejectEmojiOutsideTheFixedSet() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);

        for (String emoji : List.of("👍", "🔥🔥", "fire", " ")) {
            MockHttpServletResponse response = react(momentId, friend, emoji);
            assertThat(response.getStatus()).as(emoji).isEqualTo(400);
            JsonNode problem = api.read(response);
            assertThat(problem.get("code").asString()).isEqualTo("VALIDATION_FAILED");
            assertThat(problem.get("errors").get(0).get("field").asString()).isEqualTo("emoji");
        }
    }

    // --- Quyền ---

    // BR-23: chủ không tự thả reaction.
    @Test
    void shouldRejectReactionOnOwnMoment() throws Exception {
        TestUser owner = fixtures.newUser();
        fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);

        MockHttpServletResponse response = react(momentId, owner, "❤️");

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(api.read(response).get("code").asString()).isEqualTo("REACTION_OWN_MOMENT");
    }

    // BR-24: chỉ thả được khi đang có quyền xem; không có quyền thì 404 như không tồn tại (BR-20).
    @Test
    void shouldHideMomentFromViewersWithoutAccess() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser chosen = fixtures.befriend(owner);
        TestUser notChosen = fixtures.befriend(owner);
        String selected = fixtures.readyPhoto(owner, "SELECTED", List.of(chosen.id().toString()));
        String expired = fixtures.readyPhotoForAll(owner);
        fixtures.shiftIntoPast(expired, Duration.ofHours(24));

        assertThat(react(selected, notChosen, "🔥").getStatus()).isEqualTo(404);
        assertThat(react(selected, fixtures.newUser(), "🔥").getStatus()).isEqualTo(404);
        assertThat(react(expired, chosen, "🔥").getStatus()).isEqualTo(404);
        assertThat(unreact(selected, notChosen).getStatus()).isEqualTo(404);
    }

    // --- Chủ xem ai thả gì (US-33, BR-22) ---

    @Test
    void shouldListWhoReactedWhatForTheOwnerOnly() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser first = fixtures.befriend(owner);
        TestUser second = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);
        react(momentId, first, "😍");
        clock.advance(Duration.ofMinutes(1));
        react(momentId, second, "🔥");

        MockHttpServletResponse response = fixtures.as(get("/api/v1/moments/{id}/reactions", momentId), owner);

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode items = api.read(response).get("items");
        assertThat(items).hasSize(2);
        // Mới nhất trước.
        assertThat(items.get(0).get("user").get("id").asString()).isEqualTo(second.id().toString());
        assertThat(items.get(0).get("emoji").asString()).isEqualTo("🔥");
        assertThat(items.get(0).get("reactedAt").asString()).isNotBlank();
        assertThat(items.get(1).get("emoji").asString()).isEqualTo("😍");
        assertThat(detail(momentId, owner).get("ownerView").get("reactionCount").asInt()).isEqualTo(2);

        // BR-22: người thả chỉ thấy reaction của mình.
        assertThat(fixtures.as(get("/api/v1/moments/{id}/reactions", momentId), first).getStatus()).isEqualTo(404);
    }

    @Test
    void shouldFillReactionFieldsInTheFeed() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);
        react(momentId, friend, "😢");

        JsonNode friendItem = api.read(fixtures.as(get("/api/v1/feed"), friend)).get("items").get(0);
        JsonNode ownerItem = api.read(fixtures.as(get("/api/v1/feed"), owner)).get("items").get(0);

        assertThat(friendItem.get("myReaction").asString()).isEqualTo("😢");
        assertThat(ownerItem.get("myReaction").isNull()).isTrue();
        assertThat(ownerItem.get("ownerView").get("reactionCount").asInt()).isEqualTo(1);
    }

    // BR-18: xóa khoảnh khắc thì reaction bị xóa theo (cascade).
    @Test
    void shouldDropReactionsWithTheMoment() throws Exception {
        TestUser owner = fixtures.newUser();
        TestUser friend = fixtures.befriend(owner);
        String momentId = fixtures.readyPhotoForAll(owner);
        react(momentId, friend, "🔥");

        jdbcTemplate.update("DELETE FROM moments WHERE id = ?::uuid", momentId);

        assertThat(reactionRows(momentId)).isZero();
    }

    private MockHttpServletResponse react(String momentId, TestUser user, String emoji) throws Exception {
        return fixtures.as(api.json(put("/api/v1/moments/{id}/reaction", momentId), Map.of("emoji", emoji)), user);
    }

    private MockHttpServletResponse unreact(String momentId, TestUser user) throws Exception {
        return fixtures.as(delete("/api/v1/moments/{id}/reaction", momentId), user);
    }

    private JsonNode detail(String momentId, TestUser viewer) throws Exception {
        return api.read(fixtures.as(get("/api/v1/moments/{id}", momentId), viewer));
    }

    private int reactionRows(String momentId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM reactions WHERE moment_id = ?::uuid",
                Integer.class, momentId);
        return count == null ? 0 : count;
    }
}
