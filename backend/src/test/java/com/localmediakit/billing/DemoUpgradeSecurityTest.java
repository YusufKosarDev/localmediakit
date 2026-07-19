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
 * Graceful-enable, security side: the moment real Stripe keys are configured,
 * the demo plan switch MUST be dead — otherwise it would be a payment bypass.
 */
@SpringBootTest(properties = "app.stripe.secret-key=sk_test_realLookingKeyForThisTest")
@AutoConfigureMockMvc
class DemoUpgradeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Security User"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void demoPlanSwitchIsForbiddenWhileStripeIsConfigured() throws Exception {
        String token = register("security-demo@example.com");

        mockMvc.perform(get("/api/billing").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.stripeEnabled").value(true));

        mockMvc.perform(post("/api/billing/demo-upgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/billing/demo-downgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Plan untouched.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.plan").value("FREE"));
    }
}
