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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VersionHistoryPlanTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"History Owner"}
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

    // Accounts default to PRO now; the FREE-tier tests opt down explicitly.
    private void makeFree(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.changePlan(Plan.FREE);
        userRepository.save(user);
    }

    private long createKit(String token, String title) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private void editAndPublish(String token, long kitId, String title, String headline) throws Exception {
        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"headline\":\"%s\"}".formatted(title, headline)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void freeSeesLimitedHistoryProSeesFull() throws Exception {
        String freeToken = register("hist-free@example.com");
        makeFree("hist-free@example.com");
        long freeKit = createKit(freeToken, "Free History");
        editAndPublish(freeToken, freeKit, "Free History", "v1");
        editAndPublish(freeToken, freeKit, "Free History", "v2");
        editAndPublish(freeToken, freeKit, "Free History", "v3");

        // FREE: only the two most recent versions.
        mockMvc.perform(get("/api/mediakits/" + freeKit + "/versions")
                        .header("Authorization", "Bearer " + freeToken))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(3))
                .andExpect(jsonPath("$[1].version").value(2));

        String proToken = register("hist-pro@example.com");
        makePro("hist-pro@example.com");
        long proKit = createKit(proToken, "Pro History");
        editAndPublish(proToken, proKit, "Pro History", "v1");
        editAndPublish(proToken, proKit, "Pro History", "v2");
        editAndPublish(proToken, proKit, "Pro History", "v3");

        // PRO: full history.
        mockMvc.perform(get("/api/mediakits/" + proKit + "/versions")
                        .header("Authorization", "Bearer " + proToken))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void freeRollbackIsLimitedToVisibleWindowProIsNot() throws Exception {
        String freeToken = register("rb-free@example.com");
        makeFree("rb-free@example.com");
        long freeKit = createKit(freeToken, "Free Rollback");
        editAndPublish(freeToken, freeKit, "Free Rollback", "v1");
        editAndPublish(freeToken, freeKit, "Free Rollback", "v2");
        editAndPublish(freeToken, freeKit, "Free Rollback", "v3");

        // v1 is outside the FREE window (v3, v2 visible) -> 403.
        mockMvc.perform(post("/api/mediakits/" + freeKit + "/versions/1/activate")
                        .header("Authorization", "Bearer " + freeToken))
                .andExpect(status().isForbidden());
        // v2 is within the window -> allowed.
        mockMvc.perform(post("/api/mediakits/" + freeKit + "/versions/2/activate")
                        .header("Authorization", "Bearer " + freeToken))
                .andExpect(status().isOk());

        String proToken = register("rb-pro@example.com");
        makePro("rb-pro@example.com");
        long proKit = createKit(proToken, "Pro Rollback");
        editAndPublish(proToken, proKit, "Pro Rollback", "v1");
        editAndPublish(proToken, proKit, "Pro Rollback", "v2");
        editAndPublish(proToken, proKit, "Pro Rollback", "v3");

        // PRO can roll back to the oldest version.
        mockMvc.perform(post("/api/mediakits/" + proKit + "/versions/1/activate")
                        .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk());
    }
}
