package com.localmediakit.stats.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.stats.Platform;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The manual-sync throttle, which the rest of the suite deliberately turns off.
 *
 * <p>{@link StatsSyncFlowTest} runs syncs back to back and so sets the cooldown
 * to zero; that left the throttle itself with no coverage at all, and a change
 * that disabled it in production would have passed every test. This class owns
 * the one context where it is switched on.
 */
@SpringBootTest(properties = "app.statsync.manual-cooldown-ms=60000")
@AutoConfigureMockMvc
class StatsSyncCooldownTest {

    @TestConfiguration
    static class FakeProviderConfig {

        @Bean
        StatsProvider fakeYouTubeProvider() {
            return new StatsProvider() {
                @Override
                public Platform platform() {
                    return Platform.YOUTUBE;
                }

                @Override
                public FetchedStats fetch(String externalId) {
                    return new FetchedStats(1000L, 500L, null, null);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aSecondManualSyncInsideTheWindowIsRefused() throws Exception {
        String token = register("sync-cooldown@example.com");
        long kitId = createKit(token);

        // Connecting validates the channel with a real fetch, so the source is
        // already inside its cooldown window by the time this returns.
        mockMvc.perform(put("/api/mediakits/" + kitId + "/sources/YOUTUBE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"@kanalim\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mediakits/" + kitId + "/sources/YOUTUBE/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Senkroncu"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createKit(String token) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cooldown Kit\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }
}
