package com.localmediakit.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.auth.EmailAlreadyUsedException;
import com.localmediakit.auth.InvalidCredentialsException;
import com.localmediakit.mediakit.InvalidAppearanceException;
import com.localmediakit.mediakit.ReservedSlugException;
import com.localmediakit.user.PlanLimitExceededException;
import com.localmediakit.user.ProtectedAccountException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Error responses carry a machine code beside the human message, so the client
 * can show the text in the reader's language without the backend having to
 * know what that language is.
 *
 * <p>Codes are derived from exception class names, which makes them free to
 * add and impossible to forget — but also means a rename would silently change
 * one. The codes the frontend actually translates are pinned here, so that
 * rename fails the build instead of quietly reverting a message to Turkish.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorCodeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void codesAreDerivedFromTheExceptionName() {
        assertThat(ApiExceptionHandler.codeFor(new EmailAlreadyUsedException("x")))
                .isEqualTo("EMAIL_ALREADY_USED");
        assertThat(ApiExceptionHandler.codeFor(new InvalidCredentialsException("x")))
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(ApiExceptionHandler.codeFor(new PlanLimitExceededException("x")))
                .isEqualTo("PLAN_LIMIT_EXCEEDED");
        assertThat(ApiExceptionHandler.codeFor(new ProtectedAccountException("x")))
                .isEqualTo("PROTECTED_ACCOUNT");
        assertThat(ApiExceptionHandler.codeFor(new InvalidAppearanceException("x")))
                .isEqualTo("INVALID_APPEARANCE");
        assertThat(ApiExceptionHandler.codeFor(new UnsupportedLocaleException("x")))
                .isEqualTo("UNSUPPORTED_LOCALE");
        assertThat(ApiExceptionHandler.codeFor(new ReservedSlugException("x")))
                .isEqualTo("RESERVED_SLUG");
    }

    @Test
    void aRealErrorResponseCarriesBothTheCodeAndTheMessage() throws Exception {
        String email = "kodtest@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret","displayName":"Uretici"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        // The message stays: a client that does not know the code still has
        // something to show, which is what keeps this change additive.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret","displayName":"Uretici"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void validationAndMalformedBodiesGetTheirOwnCodes() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"x","displayName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void badCredentialsAreCodedTheSameWayEverywhere() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"yok@example.com","password":"wrongpassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).get("code").asText())
                .isEqualTo("INVALID_CREDENTIALS");
    }
}
