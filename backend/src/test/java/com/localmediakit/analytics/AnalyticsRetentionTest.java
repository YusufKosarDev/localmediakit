package com.localmediakit.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.mediakit.MediaKitVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retention's whole obligation is that a creator cannot tell it ran.
 *
 * <p>The raw rows go away, so the disk stops growing; the numbers on the
 * dashboard stay exactly where they were. A test that only asserted "old rows
 * were deleted" would pass just as happily for an implementation that walked a
 * creator's lifetime view count backwards, which is the failure worth catching.
 */
@SpringBootTest(properties = "app.analytics.retention-days=30")
@AutoConfigureMockMvc
class AnalyticsRetentionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalyticsRetentionService retentionService;

    @Autowired
    private PageViewRepository pageViewRepository;

    @Autowired
    private MediaKitVersionRepository versionRepository;

    @Test
    void foldingOldViewsAwayLeavesTheLifetimeNumbersWhereTheyWere() throws Exception {
        String token = register("retention@example.com");
        long kitId = publishedKit(token, "retention-kiti");

        // Two visitors on one old day, one on another, one inside the window.
        // Distinct hashes per day is what the real fingerprint produces: it
        // includes the date, so a returning visitor is a new hash tomorrow.
        recordView(kitId, "retention-kiti", "old-day-1-visitor-a", 200);
        recordView(kitId, "retention-kiti", "old-day-1-visitor-b", 200);
        recordView(kitId, "retention-kiti", "old-day-2-visitor-a", 100);
        recordView(kitId, "retention-kiti", "recent-visitor", 1);

        String before = analytics(token, kitId);
        long totalBefore = objectMapper.readTree(before).get("totalViews").asLong();
        long uniqueBefore = objectMapper.readTree(before).get("uniqueVisitors").asLong();
        assertThat(totalBefore).isEqualTo(4);
        assertThat(uniqueBefore).isEqualTo(4);

        int folded = retentionService.runRetentionBatch();

        assertThat(folded).as("two old days should have been folded").isEqualTo(2);
        // The raw rows really are gone -- otherwise this is a no-op that passes.
        assertThat(pageViewRepository.countByMediaKitId(kitId))
                .as("only the view inside the window should remain raw")
                .isEqualTo(1);

        mockMvc.perform(get("/api/mediakits/" + kitId + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalViews").value(totalBefore))
                .andExpect(jsonPath("$.uniqueVisitors").value(uniqueBefore));
    }

    @Test
    void aSecondPassOverTheSameDayDoesNotDoubleCount() throws Exception {
        String token = register("retention-twice@example.com");
        long kitId = publishedKit(token, "retention-twice-kiti");

        recordView(kitId, "retention-twice-kiti", "twice-visitor-a", 90);
        retentionService.runRetentionBatch();

        // A view for an already-folded day arriving late: the rollup has to
        // absorb it, not replace the day with it and not count it twice.
        recordView(kitId, "retention-twice-kiti", "twice-visitor-b", 90);
        retentionService.runRetentionBatch();

        mockMvc.perform(get("/api/mediakits/" + kitId + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalViews").value(2))
                .andExpect(jsonPath("$.uniqueVisitors").value(2));
    }

    @Test
    void nothingInsideTheWindowIsTouched() throws Exception {
        String token = register("retention-window@example.com");
        long kitId = publishedKit(token, "retention-window-kiti");

        recordView(kitId, "retention-window-kiti", "window-visitor", 5);

        assertThat(retentionService.runRetentionBatch()).isZero();
        assertThat(pageViewRepository.countByMediaKitId(kitId)).isEqualTo(1);
    }

    private void recordView(long kitId, String slug, String visitorHash, int daysAgo) {
        pageViewRepository.save(new PageView(kitId, slug, visitorHash, null, "DESKTOP",
                Instant.now().minus(daysAgo, ChronoUnit.DAYS)));
    }

    private String analytics(String token, long kitId) throws Exception {
        return mockMvc.perform(get("/api/mediakits/" + kitId + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Arsivci"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    /** Analytics resolve through an ACTIVE published version, so publish first. */
    private long publishedKit(String token, String slug) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Arsiv Kiti\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long kitId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(versionRepository.findActiveBySlug(slug)).isPresent();
        return kitId;
    }
}
