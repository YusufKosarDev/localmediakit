package com.localmediakit.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The mail starter auto-registers a health indicator that opens an SMTP
 * connection, which made the whole application report DOWN as soon as lead
 * notifications were added without SMTP configured.
 *
 * <p>That is not a cosmetic detail: the platform restarts instances on a
 * failed health check, so an optional, deliberately-dark integration would
 * have turned into an outage. Notifications are opt-in by design — their
 * absence has to be invisible to health.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthUnaffectedByMailTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theApplicationIsHealthyWithNoMailProviderConfigured() throws Exception {
        // The test context configures no SMTP host, exactly like a deployment
        // that has not opted into notifications.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
