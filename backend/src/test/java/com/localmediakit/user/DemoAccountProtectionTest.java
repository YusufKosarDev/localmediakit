package com.localmediakit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.demo.DemoDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo account's credentials are printed on the login page for anyone to
 * use. Without a guard, the first visitor to open the settings page could
 * change its password — locking every other visitor out until the nightly
 * reset — or delete it outright. Destructive operations are refused on it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoAccountProtectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String demoToken;

    /**
     * Demo seeding is off in tests, so the account is registered directly.
     * The guard keys off the address alone — how the row got there is exactly
     * what it must not depend on.
     *
     * <p>The context (and its database) is shared across the methods in this
     * class, so registration is done once and every method signs in.
     */
    @BeforeEach
    void signInAsDemo() throws Exception {
        if (userRepository.findByEmail(DemoDataService.DEMO_EMAIL).isEmpty()) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"%s","displayName":"Demo Kullanici"}
                                    """.formatted(DemoDataService.DEMO_EMAIL, DemoDataService.DEMO_PASSWORD)))
                    .andExpect(status().isOk());
        }
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(DemoDataService.DEMO_EMAIL, DemoDataService.DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        demoToken = objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void refusesToChangeTheDemoPassword() throws Exception {
        mockMvc.perform(post("/api/me/password")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"hijacked123"}
                                """.formatted(DemoDataService.DEMO_PASSWORD)))
                .andExpect(status().isForbidden());

        // The published credentials still work for the next visitor.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(DemoDataService.DEMO_EMAIL, DemoDataService.DEMO_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void refusesToChangeTheDemoEmail() throws Exception {
        mockMvc.perform(post("/api/me/email")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newEmail":"stolen@example.com"}
                                """.formatted(DemoDataService.DEMO_PASSWORD)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + demoToken))
                .andExpect(jsonPath("$.email").value(DemoDataService.DEMO_EMAIL));
    }

    @Test
    void refusesToDeleteTheDemoAccount() throws Exception {
        mockMvc.perform(delete("/api/me")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","confirmation":"HESABIMI SIL"}
                                """.formatted(DemoDataService.DEMO_PASSWORD)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isOk());
    }

    @Test
    void stillAllowsHarmlessProfileEditsSoTheSettingsPageIsExplorable() throws Exception {
        // Nothing here can lock anyone out, and the nightly reset undoes it.
        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + demoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Gezgin","theme":"DARK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Gezgin"))
                .andExpect(jsonPath("$.theme").value("DARK"));
    }
}
