package com.localmediakit.billing;

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

/**
 * Graceful-enable, demo side: with the default (dummy) Stripe config the demo
 * plan switch is available and grants/removes real PRO capabilities.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoUpgradeFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Demo User"}
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
    void billingStatusReportsStripeDisabled() throws Exception {
        String token = register("demo-status@example.com");
        mockMvc.perform(get("/api/billing").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stripeEnabled").value(false))
                // Accounts now default to PRO.
                .andExpect(jsonPath("$.plan").value("PRO"));
    }

    @Test
    void demoUpgradeGrantsRealProCapabilitiesAndDowngradeRevokesThem() throws Exception {
        String token = register("demo-upgrade@example.com");

        mockMvc.perform(post("/api/billing/demo-upgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.subscriptionStatus").value("demo"));

        // Real PRO capabilities: second kit allowed, publish without badge,
        // detailed analytics visible.
        long kit1 = createKit(token, "Demo Kit Bir");
        long kit2 = createKit(token, "Demo Kit Iki");
        mockMvc.perform(post("/api/mediakits/" + kit2 + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/demo-kit-iki"))
                .andExpect(jsonPath("$.showBadge").value(false));
        mockMvc.perform(get("/api/mediakits/" + kit2 + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.viewsByDay").exists());

        // Demo downgrade: FREE rules come back (creation + excess republish gated).
        mockMvc.perform(post("/api/billing/demo-downgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.subscriptionStatus").value("canceled"));
        mockMvc.perform(post("/api/mediakits/" + kit2 + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/mediakits/" + kit1 + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // The published page survives, as with a real downgrade.
        mockMvc.perform(get("/api/public/kits/demo-kit-iki"))
                .andExpect(status().isOk());
    }

    @Test
    void demoEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/billing/demo-upgrade"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/billing/demo-downgrade"))
                .andExpect(status().isUnauthorized());
    }
}
