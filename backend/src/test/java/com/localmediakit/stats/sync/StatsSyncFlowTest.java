package com.localmediakit.stats.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.stats.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StatsSyncFlowTest {

    /**
     * Deterministic stand-in for the YouTube provider. The real
     * {@link YouTubeStatsProvider} bean also exists but reports unavailable in
     * tests (no API key), so the registry always resolves this one.
     */
    @TestConfiguration
    static class FakeProviderConfig {

        static volatile long followers = 1000L;
        static volatile StatsProviderException.Kind failure = null;

        @Bean
        StatsProvider fakeYouTubeProvider() {
            return new StatsProvider() {
                @Override
                public Platform platform() {
                    return Platform.YOUTUBE;
                }

                @Override
                public FetchedStats fetch(String externalId) {
                    if (failure != null) {
                        throw new StatsProviderException(failure, "fake " + failure);
                    }
                    if (externalId.contains("yok")) {
                        throw new StatsProviderException(
                                StatsProviderException.Kind.NOT_FOUND, "channel not found");
                    }
                    return new FetchedStats(followers, 500L, null, null);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StatsSyncService syncService;

    @Autowired
    private StatsSourceRepository sourceRepository;

    @AfterEach
    void resetFake() {
        FakeProviderConfig.followers = 1000L;
        FakeProviderConfig.failure = null;
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

    private long createKit(String token, String title) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private void connect(String token, long kitId, String externalId) throws Exception {
        mockMvc.perform(put("/api/mediakits/" + kitId + "/sources/YOUTUBE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"" + externalId + "\"}"))
                .andExpect(status().isOk());
    }

    private void backdateLastSync(long kitId) {
        StatsSource source = sourceRepository
                .findByMediaKitIdAndPlatform(kitId, Platform.YOUTUBE).orElseThrow();
        source.recordSuccess(Instant.now().minus(2, ChronoUnit.DAYS));
        sourceRepository.save(source);
    }

    @Test
    void connectValidatesTheChannelAndLandsTheFirstMeasurement() throws Exception {
        String token = register("sync-connect@example.com");
        long kitId = createKit(token, "Senkron Kit");

        mockMvc.perform(put("/api/mediakits/" + kitId + "/sources/YOUTUBE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"@kanalim\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("YOUTUBE"))
                .andExpect(jsonPath("$.externalId").value("@kanalim"))
                .andExpect(jsonPath("$.lastSyncedAt").exists())
                .andExpect(jsonPath("$.lastError").value(org.hamcrest.Matchers.nullValue()));

        // The validating fetch doubled as the first data point.
        mockMvc.perform(get("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].platform").value("YOUTUBE"))
                .andExpect(jsonPath("$[0].followers").value(1000))
                .andExpect(jsonPath("$[0].avgViews").value(500));

        mockMvc.perform(get("/api/mediakits/" + kitId + "/sources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.availablePlatforms").value(org.hamcrest.Matchers.hasItem("YOUTUBE")))
                .andExpect(jsonPath("$.sources.length()").value(1));
    }

    @Test
    void connectingAMissingChannelIs400AndStoresNothing() throws Exception {
        String token = register("sync-missing@example.com");
        long kitId = createKit(token, "Kayip Kanal");

        mockMvc.perform(put("/api/mediakits/" + kitId + "/sources/YOUTUBE")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"@boyle-kanal-yok\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/mediakits/" + kitId + "/sources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    @Test
    void unconfiguredPlatformAnswers503() throws Exception {
        String token = register("sync-unconf@example.com");
        long kitId = createKit(token, "Kapali Platform");

        // No available provider claims INSTAGRAM in the test context.
        mockMvc.perform(put("/api/mediakits/" + kitId + "/sources/INSTAGRAM")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"hesabim\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void manualSyncAppendsAndFailuresLandInLastError() throws Exception {
        String token = register("sync-manual@example.com");
        long kitId = createKit(token, "Elle Senkron");
        connect(token, kitId, "@kanalim");

        // Provider breaks: sync answers 200 with the failure filed on the source.
        FakeProviderConfig.failure = StatsProviderException.Kind.TRANSIENT;
        mockMvc.perform(post("/api/mediakits/" + kitId + "/sources/YOUTUBE/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastError").value("fake TRANSIENT"));

        // Provider recovers with new numbers: latest view follows the series.
        FakeProviderConfig.failure = null;
        FakeProviderConfig.followers = 2000L;
        mockMvc.perform(post("/api/mediakits/" + kitId + "/sources/YOUTUBE/sync")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastError").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].followers").value(2000));
    }

    @Test
    void scheduledBatchRefreshesOnlyProOwnersDueSources() throws Exception {
        String token = register("sync-batch@example.com");
        // Accounts now default to PRO; drop to FREE for the "skipped" half of the test.
        mockMvc.perform(post("/api/billing/demo-downgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long kitId = createKit(token, "Batch Kit");
        connect(token, kitId, "@kanalim");
        backdateLastSync(kitId);
        FakeProviderConfig.followers = 3000L;

        // FREE owner: due but skipped by the batch.
        assertThat(syncService.runSyncBatch()).isZero();
        mockMvc.perform(get("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].followers").value(1000));

        // PRO owner: the same due source is refreshed.
        mockMvc.perform(post("/api/billing/demo-upgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(syncService.runSyncBatch()).isEqualTo(1);
        mockMvc.perform(get("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].followers").value(3000));

        // Freshly synced: nothing due on the next tick.
        assertThat(syncService.runSyncBatch()).isZero();
    }

    @Test
    void quotaExhaustionAbortsTheBatch() throws Exception {
        String token = register("sync-quota@example.com");
        mockMvc.perform(post("/api/billing/demo-upgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long kitA = createKit(token, "Kota A");
        long kitB = createKit(token, "Kota B");
        connect(token, kitA, "@kanal-a");
        connect(token, kitB, "@kanal-b");
        backdateLastSync(kitA);
        backdateLastSync(kitB);

        // Both due, but the first QUOTA failure stops the batch: 1 attempt only.
        FakeProviderConfig.failure = StatsProviderException.Kind.QUOTA;
        assertThat(syncService.runSyncBatch()).isEqualTo(1);
    }

    @Test
    void sourcesAreOwnerScopedAndDisconnectRemoves() throws Exception {
        String owner = register("sync-owner@example.com");
        String stranger = register("sync-stranger@example.com");
        long kitId = createKit(owner, "Sahipli Senkron");
        connect(owner, kitId, "@kanalim");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/sources")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/mediakits/" + kitId + "/sources/YOUTUBE")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/mediakits/" + kitId + "/sources/YOUTUBE")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/mediakits/" + kitId + "/sources/YOUTUBE/sync")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
    }
}
