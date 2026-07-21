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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Input-hardening: URL scheme validation and password length bounds. */
@SpringBootTest
@AutoConfigureMockMvc
class HardeningFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Hard"}
                """.formatted(email);
        String res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("token").asText();
    }

    private long createKit(String token, String body) throws Exception {
        String res = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("id").asLong();
    }

    @Test
    void avatarUrlMustBeHttpsOrEmpty() throws Exception {
        String token = register("harden-url@example.com");
        User u = userRepository.findByEmail("harden-url@example.com").orElseThrow();
        u.changePlan(Plan.PRO); // PRO so the two valid creates are not blocked by the FREE limit
        userRepository.save(u);
        // http:// rejected
        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"avatarUrl\":\"http://evil.example/x.png\"}"))
                .andExpect(status().isBadRequest());
        // javascript: rejected
        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"avatarUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
        // https accepted; empty accepted
        createKit(token, "{\"title\":\"Ok Https\",\"avatarUrl\":\"https://cdn.example/a.png\"}");
        createKit(token, "{\"title\":\"Ok Empty\",\"avatarUrl\":\"\"}");
    }

    @Test
    void collaborationLogoUrlMustBeHttps() throws Exception {
        String token = register("harden-logo@example.com");
        long kitId = createKit(token, "{\"title\":\"Logo Kit\"}");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"B\",\"logoUrl\":\"http://x/y.png\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"B\",\"logoUrl\":\"https://x/y.png\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void registerPasswordBounds() throws Exception {
        // too short
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@example.com\",\"password\":\"short\",\"displayName\":\"S\"}"))
                .andExpect(status().isBadRequest());
        // too long (> 72 bytes, BCrypt truncation guard)
        String tooLong = "a".repeat(73);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"long@example.com\",\"password\":\"" + tooLong + "\",\"displayName\":\"L\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void kitPasswordMinimumRaised() throws Exception {
        String token = register("harden-kitpw@example.com");
        User u = userRepository.findByEmail("harden-kitpw@example.com").orElseThrow();
        u.changePlan(Plan.PRO);
        userRepository.save(u);
        long kitId = createKit(token, "{\"title\":\"Pw Kit\"}");
        // 5 chars rejected (min is now 6)
        mockMvc.perform(put("/api/mediakits/" + kitId + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"12345\"}"))
                .andExpect(status().isBadRequest());
        // 6 chars accepted
        mockMvc.perform(put("/api/mediakits/" + kitId + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123456\"}"))
                .andExpect(status().isNoContent());
    }
}
