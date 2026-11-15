package com.dev.moment.user;

import static com.dev.moment.support.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.dev.moment.IntegrationTest;
import com.dev.moment.support.ApiTestClient;
import com.dev.moment.support.RecordingEmailSender;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** US-06, US-07, BR-02 và quy tắc PROFILE_INCOMPLETE (danh-sach-api.md mục 1). */
@IntegrationTest
@Import(UserProfileTests.ProbeController.class)
class UserProfileTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private RecordingEmailSender emails;

    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(mockMvc, jsonMapper, emails);
    }

    @Test
    void shouldReturnMyAccountWithoutPasswordHash() throws Exception {
        String email = uniqueEmail();
        String accessToken = api.accessTokenForNewUser(email);

        MockHttpServletResponse response = api.me(accessToken);

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = api.read(response);
        assertThat(body.get("email").asString()).isEqualTo(email);
        assertThat(body.get("username").isNull()).isTrue();
        assertThat(body.get("friendCount").asInt()).isZero();
        assertThat(body.get("isDemo").asBoolean()).isFalse();
        assertThat(response.getContentAsString()).doesNotContain("password");
    }

    @Test
    void shouldSaveUsernameInLowercaseAndFinishOnboarding() throws Exception {
        String accessToken = api.accessTokenForNewUser(uniqueEmail());
        String username = uniqueUsername();

        MockHttpServletResponse response = patchMe(accessToken,
                Map.of("username", username.toUpperCase(), "displayName", "Minh Anh", "locale", "en"));

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = api.read(response);
        assertThat(body.get("username").asString()).isEqualTo(username);
        assertThat(body.get("displayName").asString()).isEqualTo("Minh Anh");
        assertThat(body.get("locale").asString()).isEqualTo("en");
        assertThat(body.get("needsOnboarding").asBoolean()).isFalse();
    }

    @Test
    void shouldRejectUsernameTakenIgnoringCase() throws Exception {
        String username = uniqueUsername();
        api.accessTokenForOnboardedUser(uniqueEmail(), username);
        String other = api.accessTokenForNewUser(uniqueEmail());

        MockHttpServletResponse response = patchMe(other, Map.of("username", username.toUpperCase()));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(api.read(response).get("code").asString()).isEqualTo("USERNAME_TAKEN");
    }

    @Test
    void shouldRejectUsernameOutsideAllowedFormat() throws Exception {
        String accessToken = api.accessTokenForNewUser(uniqueEmail());

        for (String invalid : new String[] {"ab", "có-dấu", "a".repeat(21), "space name"}) {
            MockHttpServletResponse response = patchMe(accessToken, Map.of("username", invalid));
            assertThat(response.getStatus()).as(invalid).isEqualTo(400);
            assertThat(api.read(response).get("errors").get(0).get("code").asString()).isEqualTo("INVALID_FORMAT");
        }
    }

    @Test
    void shouldLimitDisplayNameTo40VisibleCharacters() throws Exception {
        String accessToken = api.accessTokenForNewUser(uniqueEmail());

        assertThat(patchMe(accessToken, Map.of("displayName", "😀".repeat(40))).getStatus()).isEqualTo(200);
        MockHttpServletResponse tooLong = patchMe(accessToken, Map.of("displayName", "ệ".repeat(41)));

        assertThat(tooLong.getStatus()).isEqualTo(400);
        assertThat(api.read(tooLong).get("errors").get(0).get("code").asString()).isEqualTo("TOO_LONG");
    }

    @Test
    void shouldReportUsernameAvailability() throws Exception {
        String username = uniqueUsername();
        String accessToken = api.accessTokenForOnboardedUser(uniqueEmail(), username);

        assertThat(availability(accessToken, username.toUpperCase())).isFalse();
        assertThat(availability(accessToken, uniqueUsername())).isTrue();
    }

    @Test
    void shouldBlockOtherApisUntilUsernameIsChosen() throws Exception {
        String accessToken = api.accessTokenForNewUser(uniqueEmail());

        MockHttpServletResponse blocked = probe(accessToken);
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(api.read(blocked).get("code").asString()).isEqualTo("PROFILE_INCOMPLETE");

        patchMe(accessToken, Map.of("username", uniqueUsername()));
        assertThat(probe(accessToken).getStatus()).isEqualTo(200);
    }

    private boolean availability(String accessToken, String username) throws Exception {
        return api.read(mockMvc.perform(get("/api/v1/usernames/{username}/availability", username)
                .header("Authorization", "Bearer " + accessToken)).andReturn().getResponse())
                .get("available").asBoolean();
    }

    private MockHttpServletResponse patchMe(String accessToken, Map<String, String> body) throws Exception {
        return mockMvc.perform(api.json(patch("/api/v1/me"), body).header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse probe(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/test-probe").header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse();
    }

    private static String uniqueUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/test-probe")
        Map<String, Boolean> probe() {
            return Map.of("ok", true);
        }
    }
}
