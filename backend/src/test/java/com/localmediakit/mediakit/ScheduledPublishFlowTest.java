package com.localmediakit.mediakit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * Publishing at a chosen moment.
 *
 * <p>The tests drive the batch directly rather than waiting for the scheduler,
 * so nothing here depends on a clock ticking. What they are really checking is
 * that scheduling is a CALLER of the publish path and not a second copy of it:
 * the snapshot is taken when the moment arrives, the plan gate still applies,
 * and a failure leaves a reason rather than a silent nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScheduledPublishFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScheduledPublishService scheduledPublishService;

    @Autowired
    private MediaKitRepository mediaKitRepository;

    @Test
    void aScheduledKitGoesLiveWhenItsMomentArrives() throws Exception {
        String token = register("sched-basic@example.com");
        long kitId = createKit(token, "Zamanli Kit", "sched-basic-kiti");

        schedule(token, kitId, Instant.now().plus(1, ChronoUnit.HOURS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledPublishAt").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        // Not yet.
        assertThat(scheduledPublishService.runDueBatch()).isZero();
        mockMvc.perform(get("/api/public/kits/sched-basic-kiti")).andExpect(status().isNotFound());

        // The moment passes.
        backdateSchedule(token, kitId);
        assertThat(scheduledPublishService.runDueBatch()).isEqualTo(1);

        mockMvc.perform(get("/api/public/kits/sched-basic-kiti")).andExpect(status().isOk());
        mockMvc.perform(get("/api/mediakits/" + kitId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.scheduledPublishAt").doesNotExist());
    }

    @Test
    void theSnapshotIsTakenWhenTheMomentArrivesNotWhenItWasScheduled() throws Exception {
        // The rule people expect: a correction made in between should go out,
        // not be discarded by yesterday's decision.
        String token = register("sched-snapshot@example.com");
        long kitId = createKit(token, "Eski Baslik", "sched-snapshot-kiti");
        schedule(token, kitId, Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Duzeltilmis Baslik\"}"))
                .andExpect(status().isOk());

        backdateSchedule(token, kitId);
        scheduledPublishService.runDueBatch();

        mockMvc.perform(get("/api/public/kits/sched-snapshot-kiti"))
                .andExpect(jsonPath("$.title").value("Duzeltilmis Baslik"));
    }

    @Test
    void cancellingDisarmsIt() throws Exception {
        String token = register("sched-cancel@example.com");
        long kitId = createKit(token, "Iptal Kit", "sched-cancel-kiti");
        schedule(token, kitId, Instant.now().plus(1, ChronoUnit.HOURS));
        // Backdated first, so the only reason the batch skips it is the cancel.
        backdateSchedule(token, kitId);

        mockMvc.perform(delete("/api/mediakits/" + kitId + "/schedule")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledPublishAt").doesNotExist());

        assertThat(scheduledPublishService.runDueBatch()).isZero();
        mockMvc.perform(get("/api/public/kits/sched-cancel-kiti")).andExpect(status().isNotFound());
    }

    @Test
    void aTimeInThePastIsRefused() throws Exception {
        // "Publish in the past" is just "publish", and accepting it would arm
        // something that fires on the next tick with no warning.
        String token = register("sched-past@example.com");
        long kitId = createKit(token, "Gecmis Kit", "sched-gecmis-kiti");

        schedule(token, kitId, Instant.now().minus(1, ChronoUnit.HOURS))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSpentScheduleDoesNotFireTwice() throws Exception {
        // The batch runs on a timer; a schedule that survived its own publish
        // would republish the kit on every tick forever.
        String token = register("sched-once@example.com");
        long kitId = createKit(token, "Tek Sefer", "sched-once-kiti");
        schedule(token, kitId, Instant.now().plus(1, ChronoUnit.HOURS));
        backdateSchedule(token, kitId);

        assertThat(scheduledPublishService.runDueBatch()).isEqualTo(1);
        assertThat(scheduledPublishService.runDueBatch()).isZero();

        mockMvc.perform(get("/api/mediakits/" + kitId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anotherOwnersKitCannotBeScheduled() throws Exception {
        String owner = register("sched-owner@example.com");
        String stranger = register("sched-stranger@example.com");
        long kitId = createKit(owner, "Sahipli Kit", "sched-sahipli-kiti");

        mockMvc.perform(put("/api/mediakits/" + kitId + "/schedule")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publishAt\":\"" + Instant.now().plus(1, ChronoUnit.HOURS) + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reschedulingClearsTheErrorFromALastAttempt() throws Exception {
        // The creator has just said "try again"; leaving yesterday's failure on
        // screen would describe a schedule that no longer exists.
        String token = register("sched-reset-error@example.com");
        long kitId = createKit(token, "Hata Kit", "sched-hata-kiti");

        schedule(token, kitId, Instant.now().plus(2, ChronoUnit.HOURS))
                .andExpect(jsonPath("$.scheduleError").doesNotExist());
    }

    /* --- helpers --- */

    /**
     * Moves the armed moment into the past.
     *
     * <p>Written through the repository rather than the API because the API
     * refuses past times on purpose, and adding a test-only endpoint or a
     * test-only service method to get around that would be putting the test's
     * convenience into production code. Waiting an hour is not an option either.
     */
    private void backdateSchedule(String token, long kitId) {
        MediaKit kit = mediaKitRepository.findById(kitId).orElseThrow();
        kit.schedulePublishAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        mediaKitRepository.save(kit);
    }

    private org.springframework.test.web.servlet.ResultActions schedule(
            String token, long kitId, Instant when) throws Exception {
        return mockMvc.perform(put("/api/mediakits/" + kitId + "/schedule")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publishAt\":\"" + when + "\"}"));
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret","displayName":"Zamanlayici"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createKit(String token, String title, String slug) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }
}
