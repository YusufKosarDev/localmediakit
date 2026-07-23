package com.localmediakit.mediakit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VersionDiffTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- pure diff unit tests ---

    private static MediaKitSnapshot snapshot(String headline,
                                             List<MediaKitSnapshot.PlatformStatSnapshot> platforms,
                                             List<MediaKitSnapshot.RateCardSnapshot> rateCard) {
        return new MediaKitSnapshot("slug", "Baslik", headline, null, "light", "Uretici",
                platforms, List.of(), List.of(), true, rateCard, true);
    }

    @Test
    void computeReportsFieldPlatformAndRateCardChanges() {
        MediaKitSnapshot v1 = snapshot("eski",
                List.of(new MediaKitSnapshot.PlatformStatSnapshot(
                        "YOUTUBE", 1000, null, null, null, new BigDecimal("8.00"), null)),
                List.of(new MediaKitSnapshot.RateCardSnapshot(
                        "Video", new BigDecimal("100"), "TRY", null)));
        MediaKitSnapshot v2 = snapshot("yeni",
                List.of(
                        new MediaKitSnapshot.PlatformStatSnapshot(
                                "YOUTUBE", 2000, null, null, null, new BigDecimal("8.0"), null),
                        new MediaKitSnapshot.PlatformStatSnapshot(
                                "TIKTOK", 500, null, null, null, null, null)),
                List.of(new MediaKitSnapshot.RateCardSnapshot(
                        "Video", new BigDecimal("150"), "TRY", null)));

        VersionDiffResponse diff = VersionDiffService.compute(1, 2, v1, v2);

        assertThat(diff.fields()).containsExactly(
                new VersionDiffResponse.FieldChange("headline", "eski", "yeni"));
        // YOUTUBE: followers changed; 8.00 vs 8.0 is NOT a change (BigDecimal compareTo).
        assertThat(diff.platforms()).hasSize(2);
        assertThat(diff.platforms().get(0).platform()).isEqualTo("YOUTUBE");
        assertThat(diff.platforms().get(0).kind()).isEqualTo("CHANGED");
        assertThat(diff.platforms().get(0).changes()).containsExactly(
                new VersionDiffResponse.MetricChange("followers", "1000", "2000"));
        assertThat(diff.platforms().get(1).kind()).isEqualTo("ADDED");
        assertThat(diff.rateCard().changed()).containsExactly(
                new VersionDiffResponse.MetricChange("Video", "100 TRY", "150 TRY"));
    }

    @Test
    void computeToleratesOldSchemaSnapshotsWithMissingLists() {
        // A pre-rate-card snapshot deserializes with null lists; diff must not crash.
        MediaKitSnapshot old = new MediaKitSnapshot("slug", "Baslik", null, null, "light",
                "Uretici", null, null, null, null, null, null);
        MediaKitSnapshot current = snapshot(null, List.of(),
                List.of(new MediaKitSnapshot.RateCardSnapshot("Video", new BigDecimal("100"), "TRY", null)));

        VersionDiffResponse diff = VersionDiffService.compute(1, 2, old, current);

        assertThat(diff.rateCard().added()).containsExactly("Video");
        // Old snapshots: badge defaults on, contact defaults off — current has contact on.
        assertThat(diff.fields()).containsExactly(
                new VersionDiffResponse.FieldChange("contactEnabled", "false", "true"));
    }

    // --- flow tests ---

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Difci"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createKit(String token, String json) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private void publish(String token, long kitId) throws Exception {
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void diffBetweenTwoPublishedVersions() throws Exception {
        String token = register("diff-flow@example.com");
        long kitId = createKit(token, "{\"title\":\"Diff Kit\",\"headline\":\"v1 hali\"}");
        publish(token, kitId);

        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Diff Kit\",\"headline\":\"v2 hali\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"INSTAGRAM\",\"followers\":1000,\"avgLikes\":50,\"avgComments\":15}"))
                .andExpect(status().isCreated());
        publish(token, kitId);

        mockMvc.perform(get("/api/mediakits/" + kitId + "/versions/diff?from=1&to=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromVersion").value(1))
                .andExpect(jsonPath("$.toVersion").value(2))
                .andExpect(jsonPath("$.fields[0].field").value("headline"))
                .andExpect(jsonPath("$.fields[0].from").value("v1 hali"))
                .andExpect(jsonPath("$.fields[0].to").value("v2 hali"))
                .andExpect(jsonPath("$.platforms[0].platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.platforms[0].kind").value("ADDED"));
    }

    @Test
    void freeCannotDiffOutsideItsVisibleWindow() throws Exception {
        String token = register("diff-window@example.com");
        long kitId = createKit(token, "{\"title\":\"Pencere Kit\"}");
        publish(token, kitId); // v1
        publish(token, kitId); // v2
        publish(token, kitId); // v3 -> FREE window = {v2, v3}

        mockMvc.perform(get("/api/mediakits/" + kitId + "/versions/diff?from=1&to=3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/versions/diff?from=2&to=3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // PRO unlocks the full history for diffing too.
        mockMvc.perform(post("/api/billing/demo-upgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/versions/diff?from=1&to=3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void diffIsOwnerScopedAndValidatesVersions() throws Exception {
        String owner = register("diff-owner@example.com");
        String stranger = register("diff-stranger@example.com");
        long kitId = createKit(owner, "{\"title\":\"Sahipli Diff\"}");
        publish(owner, kitId);

        mockMvc.perform(get("/api/mediakits/" + kitId + "/versions/diff?from=1&to=1")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/versions/diff?from=1&to=9")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
    }
}
