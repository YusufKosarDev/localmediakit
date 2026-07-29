package com.localmediakit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.demo.DemoDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Getting-started state. The steps are derived from the account's real data,
 * so these tests drive the actual flows (create, add stats, publish) rather
 * than poking at a progress marker.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret","displayName":"Yeni Uye"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createKit(String token, String title, String slug) throws Exception {
        String response = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","slug":"%s"}
                                """.formatted(title, slug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void aBrandNewAccountHasNothingDoneAndNothingDismissed() throws Exception {
        String token = register("fresh@example.com");

        mockMvc.perform(get("/api/me/onboarding").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(false))
                .andExpect(jsonPath("$.hasKit").value(false))
                .andExpect(jsonPath("$.hasStats").value(false))
                .andExpect(jsonPath("$.hasPublished").value(false))
                .andExpect(jsonPath("$.publicSlug").doesNotExist());
    }

    @Test
    void stepsLightUpAsTheUserActuallyDoesThem() throws Exception {
        String token = register("progress@example.com");
        long kitId = createKit(token, "Ilk Kit", "onboarding-ilk-kit");

        mockMvc.perform(get("/api/me/onboarding").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.hasKit").value(true))
                .andExpect(jsonPath("$.hasStats").value(false))
                .andExpect(jsonPath("$.hasPublished").value(false));

        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"platform":"YOUTUBE","followers":1000,"avgViews":500}
                                """))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/me/onboarding").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.hasStats").value(true))
                .andExpect(jsonPath("$.hasPublished").value(false));

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Publishing completes the flow and hands back the resulting URL.
        mockMvc.perform(get("/api/me/onboarding").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.hasKit").value(true))
                .andExpect(jsonPath("$.hasStats").value(true))
                .andExpect(jsonPath("$.hasPublished").value(true))
                .andExpect(jsonPath("$.publicSlug").value("onboarding-ilk-kit"));
    }

    @Test
    void dismissalSticksAndIsIdempotent() throws Exception {
        String token = register("dismisser@example.com");

        mockMvc.perform(post("/api/me/onboarding/dismiss").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/onboarding").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.dismissed").value(true));

        // Dismissing twice must not fail or reset anything.
        mockMvc.perform(post("/api/me/onboarding/dismiss").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/onboarding").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.dismissed").value(true));
    }

    @Test
    void dismissalSurvivesANewSessionSoItIsNotPerDevice() throws Exception {
        register("acrossdevices@example.com");

        String firstSession = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"acrossdevices@example.com","password":"supersecret"}
                                """))
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(post("/api/me/onboarding/dismiss").header("Authorization", "Bearer " + firstSession))
                .andExpect(status().isNoContent());

        // A second sign-in stands in for the same person on another device.
        String secondSession = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"acrossdevices@example.com","password":"supersecret"}
                                """))
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/api/me/onboarding").header("Authorization", "Bearer " + secondSession))
                .andExpect(jsonPath("$.dismissed").value(true));
    }

    @Test
    void onboardingNeedsASession() throws Exception {
        mockMvc.perform(get("/api/me/onboarding")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/me/onboarding/dismiss")).andExpect(status().isUnauthorized());
    }

    /**
     * The demo is one account browsed by a stream of different people. Storing
     * the first visitor's dismissal would leave everyone after them on an
     * unexplained dashboard, so it is deliberately dropped.
     */
    @Test
    void theSharedDemoAccountNeverRemembersADismissal() throws Exception {
        if (userRepository.findByEmail(DemoDataService.DEMO_EMAIL).isEmpty()) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"%s","displayName":"Demo Kullanici"}
                                    """.formatted(DemoDataService.DEMO_EMAIL, DemoDataService.DEMO_PASSWORD)))
                    .andExpect(status().isOk());
        }
        String token = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(DemoDataService.DEMO_EMAIL, DemoDataService.DEMO_PASSWORD)))
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        // Dismissing succeeds (no error for the client to handle)...
        mockMvc.perform(post("/api/me/onboarding/dismiss").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // ...but the next visitor still gets introduced to the product.
        mockMvc.perform(get("/api/me/onboarding").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.dismissed").value(false));
    }
}
