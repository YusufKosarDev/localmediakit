package com.localmediakit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.mediakit.MediaKitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account deletion. The rule that matters most: a deleted account leaves
 * nothing of itself reachable on the public web.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MediaKitRepository mediaKitRepository;

    private String register(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"Silinecek"}
                                """.formatted(email, password)))
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
    void deletionTakesEveryPublishedPageOfflineAndRemovesTheAccount() throws Exception {
        String token = register("leaving@example.com", "supersecret");
        long firstKit = createKit(token, "Birinci", "silinen-kit-bir");
        long secondKit = createKit(token, "Ikinci", "silinen-kit-iki");

        mockMvc.perform(post("/api/mediakits/" + firstKit + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/mediakits/" + secondKit + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Both are live before we start.
        mockMvc.perform(get("/api/public/kits/silinen-kit-bir")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/silinen-kit-iki")).andExpect(status().isOk());

        Long userId = userRepository.findByEmail("leaving@example.com").orElseThrow().getId();

        mockMvc.perform(delete("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"supersecret","confirmation":"HESABIMI SIL"}
                                """))
                .andExpect(status().isNoContent());

        // The whole public footprint is gone — this is the point of the feature.
        mockMvc.perform(get("/api/public/kits/silinen-kit-bir")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/kits/silinen-kit-iki")).andExpect(status().isNotFound());

        // Rows are really deleted, not just detached.
        assertThat(userRepository.findByEmail("leaving@example.com")).isEmpty();
        assertThat(mediaKitRepository.findByUserIdOrderByCreatedAtDesc(userId)).isEmpty();

        // The session cannot be used to reach anything afterwards.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // And the credentials no longer log in.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"leaving@example.com","password":"supersecret"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletionNeedsBothThePasswordAndTheTypedConfirmation() throws Exception {
        String token = register("staying@example.com", "supersecret");

        // Right confirmation, wrong password.
        mockMvc.perform(delete("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"notmypassword","confirmation":"HESABIMI SIL"}
                                """))
                .andExpect(status().isUnauthorized());

        // Right password, wrong confirmation phrase.
        mockMvc.perform(delete("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"supersecret","confirmation":"evet sil"}
                                """))
                .andExpect(status().isUnauthorized());

        // Survived both attempts.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void deletingOneAccountLeavesOtherAccountsAndTheirLivePagesAlone() throws Exception {
        String neighbourToken = register("neighbour@example.com", "supersecret");
        long neighbourKit = createKit(neighbourToken, "Komsu", "komsu-kit");
        mockMvc.perform(post("/api/mediakits/" + neighbourKit + "/publish")
                        .header("Authorization", "Bearer " + neighbourToken))
                .andExpect(status().isOk());

        String token = register("transient@example.com", "supersecret");
        createKit(token, "Gecici", "gecici-kit");

        mockMvc.perform(delete("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"supersecret","confirmation":"HESABIMI SIL"}
                                """))
                .andExpect(status().isNoContent());

        // The neighbour is untouched: account, kit and public page all intact.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + neighbourToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/komsu-kit")).andExpect(status().isOk());
        assertThat(userRepository.findByEmail("neighbour@example.com")).isPresent();
    }
}
