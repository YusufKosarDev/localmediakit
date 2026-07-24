package com.localmediakit.mediakit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.user.Plan;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordProtectionFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Gizli Owner"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private void makePro(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.changePlan(Plan.PRO);
        userRepository.save(user);
    }

    private long createKit(String token, String title) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"headline\":\"Gizli oranlar\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private void publish(String token, long kitId) throws Exception {
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void setPassword(String token, long kitId, String password) throws Exception {
        mockMvc.perform(put("/api/mediakits/" + kitId + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(password)))
                .andExpect(status().isNoContent());
    }

    private MockHttpServletRequestBuilder unlock(String slug, String password, String ip) {
        return post("/api/public/kits/" + slug + "/unlock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"%s\"}".formatted(password))
                .header("X-Forwarded-For", ip);
    }

    @Test
    void settingPasswordIsAProFeature() throws Exception {
        String token = register("pw-free@example.com");
        // Accounts now default to PRO; drop to FREE to exercise the gate.
        mockMvc.perform(post("/api/billing/demo-downgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long kitId = createKit(token, "Free Gizli");
        mockMvc.perform(put("/api/mediakits/" + kitId + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedKitHidesContentUntilUnlocked() throws Exception {
        String token = register("pw-protected@example.com");
        makePro("pw-protected@example.com");
        long kitId = createKit(token, "Korumali Kit");
        setPassword(token, kitId, "marka2026");
        publish(token, kitId);

        // Public GET returns only the gate: no sensitive fields, isProtected true.
        mockMvc.perform(get("/api/public/kits/korumali-kit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isProtected").value(true))
                .andExpect(jsonPath("$.title").value("Korumali Kit"))
                .andExpect(jsonPath("$.headline").doesNotExist())
                .andExpect(jsonPath("$.platforms").doesNotExist())
                .andExpect(jsonPath("$.displayName").doesNotExist());

        // Wrong password -> 401, content stays hidden.
        mockMvc.perform(unlock("korumali-kit", "yanlis", "10.0.0.1"))
                .andExpect(status().isUnauthorized());

        // Correct password -> full content.
        mockMvc.perform(unlock("korumali-kit", "marka2026", "10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isProtected").value(false))
                .andExpect(jsonPath("$.headline").value("Gizli oranlar"))
                .andExpect(jsonPath("$.displayName").value("Gizli Owner"));
    }

    @Test
    void bruteForceIsRateLimited() throws Exception {
        String token = register("pw-brute@example.com");
        makePro("pw-brute@example.com");
        long kitId = createKit(token, "Brute Kit");
        setPassword(token, kitId, "dogruSifre");
        publish(token, kitId);

        // Five wrong attempts from one client are allowed (each 401)...
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(unlock("brute-kit", "yanlis" + i, "203.0.113.77"))
                    .andExpect(status().isUnauthorized());
        }
        // ...the sixth is throttled, even with the CORRECT password.
        mockMvc.perform(unlock("brute-kit", "dogruSifre", "203.0.113.77"))
                .andExpect(status().isTooManyRequests());

        // A different client is unaffected and can still unlock.
        mockMvc.perform(unlock("brute-kit", "dogruSifre", "203.0.113.99"))
                .andExpect(status().isOk());
    }

    @Test
    void passwordIsFrozenIntoSnapshotAtPublish() throws Exception {
        String token = register("pw-snapshot@example.com");
        makePro("pw-snapshot@example.com");
        long kitId = createKit(token, "Snapshot Gizli");
        setPassword(token, kitId, "ilkSifre");
        publish(token, kitId);
        mockMvc.perform(get("/api/public/kits/snapshot-gizli"))
                .andExpect(jsonPath("$.isProtected").value(true));

        // Remove the password on the DRAFT without publishing: live page stays protected.
        mockMvc.perform(delete("/api/mediakits/" + kitId + "/password")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/kits/snapshot-gizli"))
                .andExpect(jsonPath("$.isProtected").value(true));

        // Republish -> the public page becomes unprotected (full content).
        publish(token, kitId);
        mockMvc.perform(get("/api/public/kits/snapshot-gizli"))
                .andExpect(jsonPath("$.isProtected").value(false))
                .andExpect(jsonPath("$.headline").value("Gizli oranlar"));
    }

    @Test
    void unprotectedKitServesFullContentAndUnlockIsANoop() throws Exception {
        String token = register("pw-open@example.com");
        long kitId = createKit(token, "Acik Kit");
        publish(token, kitId);

        // Full content, isProtected false: the edge-cacheable path is unchanged.
        mockMvc.perform(get("/api/public/kits/acik-kit"))
                .andExpect(jsonPath("$.isProtected").value(false))
                .andExpect(jsonPath("$.displayName").value("Gizli Owner"));

        // Unlock on an unprotected kit just returns the content (any password).
        mockMvc.perform(unlock("acik-kit", "whatever", "10.0.0.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isProtected").value(false));
    }
}
