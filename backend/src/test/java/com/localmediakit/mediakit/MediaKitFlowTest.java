package com.localmediakit.mediakit;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.localmediakit.user.Plan;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class MediaKitFlowTest {

    @Autowired    
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Owner"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void unauthenticatedCannotCreate() throws Exception {
        mockMvc.perform(post("/api/mediakits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createGeneratesSlugFromTitle() throws Exception {
        String token = register("slug-owner@example.com");
        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Benim Kanalim\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("benim-kanalim"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void explicitReservedSlugIsRejected() throws Exception {
        String token = register("reserved-owner@example.com");
        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Whatever\",\"slug\":\"admin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void freePlanAllowsOnlyOneKit() throws Exception {
        String token = register("limit-owner@example.com");
        // Accounts now default to PRO; drop to FREE to exercise the plan limit.
        setPlan("limit-owner@example.com", Plan.FREE);
        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Second\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanReadOwnButNotOthersKit() throws Exception {
        String ownerToken = register("owner-a@example.com");
        String otherToken = register("owner-b@example.com");

        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Private Kit\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long kitId = objectMapper.readTree(created).get("id").asLong();

        // Owner reads it -> 200
        mockMvc.perform(get("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // Another user reads it -> 404 (ownership enforced, existence hidden)
        mockMvc.perform(get("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void slugCollisionGetsSuffix() throws Exception {
        String tokenA = register("collide-a@example.com");
        String tokenB = register("collide-b@example.com");

        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Shared Name\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("shared-name"));

        // Different user, same title -> globally unique slug with suffix
        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Shared Name\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("shared-name-2"));
    }

    /**
     * Puts an account on a plan.
     *
     * <p>Accounts default to PRO, so the FREE-tier assertions opt down here.
     * Written straight to the repository: the endpoint that used to do this
     * belonged to the Stripe integration and went with it, and a production
     * route whose only caller is a test was never the honest way to arrange
     * a fixture.
     */
    private void setPlan(String email, Plan plan) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.changePlan(plan);
        userRepository.save(user);
    }
}
