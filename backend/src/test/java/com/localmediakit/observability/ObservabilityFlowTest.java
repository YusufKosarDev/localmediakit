package com.localmediakit.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the operator gets: an id on every response, a metrics endpoint that is
 * not open to the world, and counters that actually move.
 *
 * <p>The last one is the point. A metric that is defined but never incremented
 * reads exactly like a thing that never happens, which is the failure this
 * whole layer exists to avoid — so the assertion is on the delta across a real
 * publish rather than on the meter merely existing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void aCallersOwnRequestIdIsHonouredSoAChainSharesOne() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "upstream-123"))
                .andExpect(header().string("X-Request-Id", "upstream-123"));
    }

    @Test
    void anIdThatCouldForgeALogLineIsCleanedBeforeItIsUsed() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "ok\nINJECTED"))
                .andExpect(header().string("X-Request-Id", "okINJECTED"));
    }

    @Test
    void rejectedRequestsAreTraceableToo() throws Exception {
        // The filter runs ahead of security, so a 401 carries an id as well --
        // otherwise the requests most worth investigating are the ones that
        // cannot be found.
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void healthIsPublicButMetricsAreNot() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // How much a service is used, and when it is failing, is not something
        // to hand to an anonymous caller.
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void publishingMovesThePublishCounter() throws Exception {
        double before = counter("localmediakit.publish.completed");

        String token = register("observability-publish@example.com");
        long kitId = createKit(token);
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(counter("localmediakit.publish.completed")).isEqualTo(before + 1);
    }

    @Test
    void aRevalidationThatDoesNotLandIsCounted() throws Exception {
        // No frontend is listening in the test context, so every publish here
        // also exercises the failure path -- which is the state the dashboard
        // reports as success and nothing else would have surfaced.
        double before = counter("localmediakit.revalidation.failed");

        String token = register("observability-revalidate@example.com");
        long kitId = createKit(token);
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(counter("localmediakit.revalidation.failed")).isGreaterThan(before);
    }

    private double counter(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0d : counter.count();
    }

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Gozlemci"}
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
                        .content("{\"title\":\"Gozlemlenebilirlik Kiti\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }
}
