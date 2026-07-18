package com.localmediakit.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StatsFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformStatsRepository statsRepository;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Stats Owner"}
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
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    void everyUpdateAppendsANewRow() throws Exception {
        String token = register("series@example.com");
        long kitId = createKit(token, "Seri Kit");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"INSTAGRAM\",\"followers\":1000,\"avgLikes\":50,\"avgComments\":10}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"INSTAGRAM\",\"followers\":1200,\"avgLikes\":60,\"avgComments\":12}"))
                .andExpect(status().isCreated());

        // Time series, not an upsert: two INSTAGRAM rows exist for this kit.
        long rows = statsRepository.findAll().stream()
                .filter(s -> s.getMediaKitId().equals(kitId) && s.getPlatform() == Platform.INSTAGRAM)
                .count();
        assertEquals(2, rows);

        // The dashboard view exposes only the LATEST measurement per platform.
        mockMvc.perform(get("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].followers").value(1200))
                .andExpect(jsonPath("$[0].engagementRate").value(6.00));
    }

    @Test
    void growthBadgeComparesAgainstThirtyDayBaseline() throws Exception {
        String token = register("trend@example.com");
        long kitId = createKit(token, "Trend Kit");

        // Backdated baseline: 35 days ago the account had 10000 followers.
        statsRepository.save(new PlatformStats(kitId, Platform.YOUTUBE, 10000,
                5000L, 400L, 30L, Instant.now().minus(35, ChronoUnit.DAYS)));

        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"YOUTUBE\",\"followers\":11500,\"avgViews\":6000,\"avgLikes\":480,\"avgComments\":40}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.followerGrowth30d").value(15.0));
    }

    @Test
    void singleMeasurementHasNoGrowthBadge() throws Exception {
        String token = register("nogrowth@example.com");
        long kitId = createKit(token, "Tek Olcum");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"TIKTOK\",\"followers\":5000,\"avgLikes\":200,\"avgComments\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.followerGrowth30d").doesNotExist());
    }

    @Test
    void statsAreOwnerScoped() throws Exception {
        String owner = register("stats-owner@example.com");
        String stranger = register("stats-stranger@example.com");
        long kitId = createKit(owner, "Korunan Stats");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"INSTAGRAM\",\"followers\":10}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"INSTAGRAM\",\"followers\":10}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownPlatformIsRejected() throws Exception {
        String token = register("badplatform@example.com");
        long kitId = createKit(token, "Bilinmeyen Platform");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"MYSPACE\",\"followers\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void demographicsAreReplacedAndValidated() throws Exception {
        String token = register("demo-graphics@example.com");
        long kitId = createKit(token, "Demografi Kit");

        mockMvc.perform(put("/api/mediakits/" + kitId + "/demographics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries":[
                                  {"category":"AGE","label":"18-24","percentage":60},
                                  {"category":"AGE","label":"25-34","percentage":40},
                                  {"category":"GENDER","label":"Kadin","percentage":55}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // Replace, not merge: a second PUT fully overwrites the distribution.
        mockMvc.perform(put("/api/mediakits/" + kitId + "/demographics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries":[{"category":"COUNTRY","label":"Turkiye","percentage":80}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("COUNTRY"));

        // A category summing over 100% is rejected.
        mockMvc.perform(put("/api/mediakits/" + kitId + "/demographics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries":[
                                  {"category":"AGE","label":"18-24","percentage":70},
                                  {"category":"AGE","label":"25-34","percentage":50}
                                ]}"""))
                .andExpect(status().isBadRequest());

        // Duplicate (category, label) pairs are rejected.
        mockMvc.perform(put("/api/mediakits/" + kitId + "/demographics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries":[
                                  {"category":"AGE","label":"18-24","percentage":10},
                                  {"category":"AGE","label":"18-24","percentage":20}
                                ]}"""))
                .andExpect(status().isBadRequest());
    }
}
