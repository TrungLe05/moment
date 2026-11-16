package com.dev.moment.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dev.moment.IntegrationTest;
import com.dev.moment.common.text.MaxGraphemes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@IntegrationTest
@Import(GlobalExceptionHandlerTests.ProbeController.class)
@WithMockUser
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnFieldErrorsInVietnameseWhenBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/test/probe/validate")
                        .header("Accept-Language", "vi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"name\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.type").value("https://moment.app/errors/validation-failed"))
                .andExpect(jsonPath("$.title").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.detail").value("Có 2 trường không hợp lệ."))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("TOO_SHORT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("Tối thiểu 3 ký tự."));
    }

    @Test
    void shouldFallBackToEnglishForUnsupportedLanguage() throws Exception {
        mockMvc.perform(post("/test/probe/validate")
                        .header("Accept-Language", "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"name\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid data"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void shouldCountGraphemesWhenCheckingMaxLength() throws Exception {
        // 5 emoji gia đình: 5 ký tự hiển thị dù dài hơn nhiều code unit, nên hợp lệ với giới hạn 5.
        String fiveFamilies = "👨‍👩‍👧".repeat(5);
        mockMvc.perform(post("/test/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"name\":\"abc\",\"nickname\":\"" + fiveFamilies + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/test/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"name\":\"abc\",\"nickname\":\"" + fiveFamilies + "x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].message").value("Must be at most 5 characters."));
    }

    @Test
    void shouldReturnValidationFailedWhenBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/test/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldMapApiExceptionToItsStatusAndHeaders() throws Exception {
        mockMvc.perform(get("/test/probe/locked"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void shouldHideDetailsOfUnexpectedErrors() throws Exception {
        mockMvc.perform(get("/test/probe/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret internal state"))));
    }

    @Test
    void shouldReturnNotFoundProblemForUnknownRoute() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldEchoValidRequestIdAndReplaceInvalidOne() throws Exception {
        mockMvc.perform(get("/test/probe/locked").header("X-Request-Id", "client-req-12345"))
                .andExpect(header().string("X-Request-Id", "client-req-12345"))
                .andExpect(jsonPath("$.requestId").value("client-req-12345"));
        mockMvc.perform(get("/test/probe/locked").header("X-Request-Id", "bad id\nwith newline"))
                .andExpect(header().string("X-Request-Id",
                        org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }

    record ProbeRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 3, max = 20) String name,
            @MaxGraphemes(max = 5) String nickname) {
    }

    @RestController
    static class ProbeController {

        @PostMapping("/test/probe/validate")
        Map<String, String> validate(@Valid @RequestBody ProbeRequest request) {
            return Map.of("ok", "true");
        }

        @GetMapping("/test/probe/locked")
        void locked() {
            throw ApiException.withHeaders(ErrorCode.LOGIN_LOCKED, Map.of("Retry-After", "900"));
        }

        @GetMapping("/test/probe/crash")
        void crash() {
            throw new IllegalStateException("secret internal state");
        }
    }
}
