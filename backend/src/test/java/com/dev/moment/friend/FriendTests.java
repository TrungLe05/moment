package com.dev.moment.friend;

import static com.dev.moment.support.ApiTestClient.randomIp;
import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.RecordingEmailSender;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** E2: mã mời và kết bạn (US-10 → US-14, BR-04 → BR-07). */
@IntegrationTest
class FriendTests {

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

    // --- Mã mời (BR-04, US-10, US-14) ---

    @Test
    void shouldKeepOneActiveInviteCodeUntilRotated() throws Exception {
        TestUser owner = newUser();

        JsonNode first = api.read(authed(get("/api/v1/invites/me"), owner));
        JsonNode second = api.read(authed(get("/api/v1/invites/me"), owner));

        assertThat(first.get("code").asString()).matches("[A-Za-z0-9]{8}");
        assertThat(second.get("code")).isEqualTo(first.get("code"));
        assertThat(first.get("url").asString())
                .isEqualTo("http://localhost:3000/invite?code=" + first.get("code").asString());
    }

    @Test
    void shouldReturnSameCodeWhenFirstInviteIsFetchedConcurrently() throws Exception {
        TestUser owner = newUser();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<String> codes = new ArrayList<>();
        try {
            List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tasks.add(() -> authed(get("/api/v1/invites/me"), owner));
            }
            for (Future<MockHttpServletResponse> future : executor.invokeAll(tasks)) {
                MockHttpServletResponse response = future.get();
                assertThat(response.getStatus()).isEqualTo(200);
                codes.add(api.read(response).get("code").asString());
            }
        } finally {
            executor.shutdown();
        }

        assertThat(codes).hasSize(4).containsOnly(codes.getFirst());
    }

    @Test
    void shouldInvalidateOldCodeImmediatelyWhenRotated() throws Exception {
        TestUser owner = newUser();
        String oldCode = inviteCode(owner);

        String newCode = api.read(authed(post("/api/v1/invites/me/rotate"), owner)).get("code").asString();

        assertThat(newCode).isNotEqualTo(oldCode);
        MockHttpServletResponse oldLookup = publicInvite(oldCode);
        assertThat(oldLookup.getStatus()).isEqualTo(404);
        assertThat(api.read(oldLookup).get("code").asString()).isEqualTo("INVITE_INVALID");
        assertThat(publicInvite(newCode).getStatus()).isEqualTo(200);
    }

    @Test
    void shouldShowInviterWithoutSignIn() throws Exception {
        TestUser owner = newUser();

        MockHttpServletResponse response = publicInvite(inviteCode(owner));

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode inviter = api.read(response).get("inviter");
        assertThat(inviter.get("username").asString()).isEqualTo(owner.username());
        assertThat(inviter.has("email")).isFalse();
    }

    @Test
    void shouldRejectMalformedOrUnknownCode() throws Exception {
        assertThat(api.read(publicInvite("không-hợp-lệ")).get("code").asString()).isEqualTo("INVITE_INVALID");
        assertThat(api.read(publicInvite("Zz9Zz9Zz")).get("code").asString()).isEqualTo("INVITE_INVALID");
    }

    // --- Kết bạn (BR-05, BR-06, US-11) ---

    @Test
    void shouldBecomeFriendsImmediatelyWhenAcceptingInvite() throws Exception {
        TestUser inviter = newUser();
        TestUser invitee = newUser();

        MockHttpServletResponse response = accept(invitee, inviteCode(inviter));

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = api.read(response);
        assertThat(body.get("alreadyFriends").asBoolean()).isFalse();
        assertThat(body.get("friend").get("id").asString()).isEqualTo(inviter.id().toString());
        assertThat(friendIds(inviter)).containsExactly(invitee.id().toString());
        assertThat(friendIds(invitee)).containsExactly(inviter.id().toString());
        assertThat(api.read(api.me(invitee.accessToken())).get("friendCount").asInt()).isEqualTo(1);
    }

    @Test
    void shouldReturnAlreadyFriendsInsteadOfErrorWhenAcceptingTwice() throws Exception {
        TestUser inviter = newUser();
        TestUser invitee = newUser();
        String code = inviteCode(inviter);
        accept(invitee, code);

        MockHttpServletResponse again = accept(invitee, code);

        assertThat(again.getStatus()).isEqualTo(200);
        assertThat(api.read(again).get("alreadyFriends").asBoolean()).isTrue();
        assertThat(friendIds(invitee)).hasSize(1);
    }

    @Test
    void shouldRejectOwnInvite() throws Exception {
        TestUser owner = newUser();

        MockHttpServletResponse response = accept(owner, inviteCode(owner));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(api.read(response).get("code").asString()).isEqualTo("INVITE_SELF");
    }

    @Test
    void shouldRejectFriendshipWhenInviteeAlreadyHas15Friends() throws Exception {
        TestUser inviter = newUser();
        TestUser invitee = newUser();
        addFakeFriends(invitee.id(), 15);

        MockHttpServletResponse response = accept(invitee, inviteCode(inviter));

        assertThat(response.getStatus()).isEqualTo(422);
        JsonNode body = api.read(response);
        assertThat(body.get("code").asString()).isEqualTo("FRIEND_LIMIT_REACHED");
        assertThat(body.get("detail").asString()).isEqualTo("Bạn đã có đủ 15 bạn.");
    }

    @Test
    void shouldRejectFriendshipWhenInviterAlreadyHas15Friends() throws Exception {
        TestUser inviter = newUser();
        TestUser invitee = newUser();
        addFakeFriends(inviter.id(), 15);

        MockHttpServletResponse response = accept(invitee, inviteCode(inviter));

        assertThat(api.read(response).get("detail").asString()).isEqualTo("Người mời bạn đã có đủ 15 bạn.");
        assertThat(friendIds(invitee)).isEmpty();
    }

    @Test
    void shouldNeverExceed15FriendsWhenTwoInvitesAreAcceptedAtOnce() throws Exception {
        TestUser invitee = newUser();
        addFakeFriends(invitee.id(), 14);
        TestUser firstInviter = newUser();
        TestUser secondInviter = newUser();
        List<String> codes = List.of(inviteCode(firstInviter), inviteCode(secondInviter));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> tasks = codes.stream()
                    .<Callable<Integer>>map(code -> () -> accept(invitee, code).getStatus())
                    .toList();
            for (Future<Integer> future : executor.invokeAll(tasks)) {
                statuses.add(future.get());
            }
        } finally {
            executor.shutdown();
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 422);
        assertThat(friendCountInDatabase(invitee.id())).isEqualTo(15);
    }

    @Test
    void shouldRequireCompletedProfileToAcceptInvite() throws Exception {
        TestUser inviter = newUser();
        String accessToken = api.accessTokenForNewUser(uniqueEmail());

        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/invites/{code}/accept", inviteCode(inviter))
                .header("Authorization", "Bearer " + accessToken).with(randomIp())).andReturn().getResponse();

        assertThat(api.read(response).get("code").asString()).isEqualTo("PROFILE_INCOMPLETE");
    }

    @Test
    void shouldRequireSignInToAcceptInvite() throws Exception {
        TestUser inviter = newUser();

        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/invites/{code}/accept", inviteCode(inviter))
                .with(randomIp())).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // --- Danh sách và hủy kết bạn (US-12, US-13, BR-07) ---

    @Test
    void shouldListFriendsWithCountAndLimit() throws Exception {
        TestUser me = newUser();
        TestUser friend = newUser();
        accept(friend, inviteCode(me));

        JsonNode body = api.read(authed(get("/api/v1/friends"), me));

        assertThat(body.get("count").asInt()).isEqualTo(1);
        assertThat(body.get("limit").asInt()).isEqualTo(15);
        JsonNode item = body.get("items").get(0);
        assertThat(item.get("username").asString()).isEqualTo(friend.username());
        assertThat(item.get("friendsSince").asString()).isNotBlank();
    }

    @Test
    void shouldUnfriendForBothSidesImmediately() throws Exception {
        TestUser me = newUser();
        TestUser friend = newUser();
        accept(friend, inviteCode(me));

        MockHttpServletResponse response = authed(delete("/api/v1/friends/{id}", friend.id()), me);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(friendIds(me)).isEmpty();
        assertThat(friendIds(friend)).isEmpty();
        assertThat(authed(delete("/api/v1/friends/{id}", friend.id()), me).getStatus()).isEqualTo(404);
    }

    @Test
    void shouldAllowBecomingFriendsAgainAfterUnfriending() throws Exception {
        TestUser me = newUser();
        TestUser friend = newUser();
        String code = inviteCode(me);
        accept(friend, code);
        authed(delete("/api/v1/friends/{id}", friend.id()), me);

        MockHttpServletResponse again = accept(friend, code);

        assertThat(api.read(again).get("alreadyFriends").asBoolean()).isFalse();
        assertThat(friendIds(me)).containsExactly(friend.id().toString());
    }

    @Test
    void shouldOrderUserPairTheSameWayAsPostgresql() {
        // UUID.compareTo của Java coi bit cao là số âm nên đặt "f0..." trước "10..."; PostgreSQL thì ngược lại.
        UUID high = UUID.fromString("f0000000-0000-0000-0000-000000000000");
        UUID low = UUID.fromString("10000000-0000-0000-0000-000000000000");
        assertThat(high.compareTo(low)).isNegative();

        UserPair pair = UserPair.of(high, low);

        Boolean postgresAgrees = jdbcTemplate.queryForObject(
                "SELECT ?::uuid < ?::uuid", Boolean.class, pair.first().toString(), pair.second().toString());
        assertThat(postgresAgrees).isTrue();
        assertThat(pair.first()).isEqualTo(low);
    }

    private record TestUser(UUID id, String username, String accessToken) {
    }

    private TestUser newUser() throws Exception {
        String username = "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String accessToken = api.accessTokenForOnboardedUser(uniqueEmail(), username);
        UUID id = UUID.fromString(api.read(api.me(accessToken)).get("id").asString());
        return new TestUser(id, username, accessToken);
    }

    /** Tạo nhanh n người bạn thẳng trong database; họ không cần đăng nhập. */
    private void addFakeFriends(UUID userId, int count) {
        for (int i = 0; i < count; i++) {
            UUID friendId = jdbcTemplate.queryForObject(
                    "INSERT INTO users (email) VALUES (?) RETURNING id", UUID.class, uniqueEmail());
            UserPair pair = UserPair.of(userId, friendId);
            jdbcTemplate.update("INSERT INTO friendships (user_a_id, user_b_id) VALUES (?, ?)",
                    pair.first(), pair.second());
        }
    }

    private int friendCountInDatabase(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM friendships WHERE user_a_id = ? OR user_b_id = ?", Integer.class, userId, userId);
        return count == null ? 0 : count;
    }

    private String inviteCode(TestUser user) throws Exception {
        return api.read(authed(get("/api/v1/invites/me"), user)).get("code").asString();
    }

    private MockHttpServletResponse publicInvite(String code) throws Exception {
        return mockMvc.perform(get("/api/v1/invites/{code}", code).header("Accept-Language", "vi").with(randomIp()))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse accept(TestUser user, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/invites/{code}/accept", code)
                        .header("Authorization", "Bearer " + user.accessToken())
                        .header("Accept-Language", "vi")
                        .with(randomIp()))
                .andReturn().getResponse();
    }

    private List<String> friendIds(TestUser user) throws Exception {
        List<String> ids = new ArrayList<>();
        api.read(authed(get("/api/v1/friends"), user)).get("items").forEach(item -> ids.add(item.get("id").asString()));
        return ids;
    }

    private MockHttpServletResponse authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, TestUser user)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + user.accessToken()))
                .andReturn().getResponse();
    }
}
