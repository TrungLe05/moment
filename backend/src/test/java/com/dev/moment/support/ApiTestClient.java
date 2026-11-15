package com.dev.moment.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Thao tác API hay dùng trong test: đăng ký, xác thực, đăng nhập, đọc JSON. */
public class ApiTestClient {

    // Mật khẩu test sinh lúc chạy để không có chuỗi giống mật khẩu nằm cứng trong repo.
    public static final String PASSWORD = newPassword();

    /** Mật khẩu hợp lệ theo BR-01 (đủ 8 ký tự, dưới 72 byte), khác nhau mỗi lần gọi. */
    public static String newPassword() {
        return "pw-" + UUID.randomUUID();
    }

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;
    private final RecordingEmailSender emails;

    public ApiTestClient(MockMvc mockMvc, JsonMapper jsonMapper, RecordingEmailSender emails) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
        this.emails = emails;
    }

    /** Email chưa từng dùng, để các test không đụng nhau khi chạy chung database. */
    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** Mỗi test một IP giả để giới hạn tần suất theo IP không ảnh hưởng test khác. */
    public static RequestPostProcessor randomIp() {
        String ip = "10." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256)
                + "." + ThreadLocalRandom.current().nextInt(1, 255);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    public MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
    }

    public MockHttpServletResponse register(String email, String password) throws Exception {
        return mockMvc.perform(json(post("/api/v1/auth/register"),
                Map.of("email", email, "password", password, "locale", "vi")).with(randomIp()))
                .andReturn().getResponse();
    }

    public void registerAndVerify(String email) throws Exception {
        register(email, PASSWORD);
        String token = emails.latestToken(email, "/verify-email").orElseThrow();
        mockMvc.perform(json(post("/api/v1/auth/verify-email"), Map.of("token", token)));
    }

    public MockHttpServletResponse login(String email, String password, String clientType) throws Exception {
        MockHttpServletRequestBuilder request = json(post("/api/v1/auth/login"),
                Map.of("email", email, "password", password)).with(randomIp());
        if (clientType != null) {
            request.header("X-Client-Type", clientType);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    /** Tạo tài khoản đã xác thực, đăng nhập dạng native, trả access token. */
    public String accessTokenForNewUser(String email) throws Exception {
        registerAndVerify(email);
        return read(login(email, PASSWORD, "native")).get("accessToken").asString();
    }

    /** Như trên và chọn username luôn, để gọi được các API cần hồ sơ hoàn tất. */
    public String accessTokenForOnboardedUser(String email, String username) throws Exception {
        String accessToken = accessTokenForNewUser(email);
        mockMvc.perform(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/me"),
                Map.of("username", username, "displayName", username))
                .header("Authorization", "Bearer " + accessToken));
        return accessToken;
    }

    public MockHttpServletResponse me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse();
    }

    public JsonNode read(MockHttpServletResponse response) throws Exception {
        return jsonMapper.readTree(response.getContentAsString());
    }
}
