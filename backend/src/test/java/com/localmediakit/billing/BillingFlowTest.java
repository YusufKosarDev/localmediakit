package com.localmediakit.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BillingFlowTest {

    private static final String WEBHOOK_SECRET = "whsec_test_dummy"; // matches application.yml default

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Billing User"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long userId(String token) throws Exception {
        String me = mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(me).get("id").asLong();
    }

    private String planOf(String token) throws Exception {
        String me = mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(me).get("plan").asText();
    }

    /** Real Stripe signature scheme: t=<ts>,v1=HMAC_SHA256(secret, "<ts>.<payload>"). */
    private String sign(String payload) throws Exception {
        long t = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(
                mac.doFinal((t + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + t + ",v1=" + v1;
    }

    private void sendWebhook(String payload) throws Exception {
        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Stripe-Signature", sign(payload)))
                .andExpect(status().isOk());
    }

    private String checkoutCompleted(String eventId, long userId, String subId) {
        return """
                {"id":"%s","type":"checkout.session.completed","data":{"object":{
                 "id":"cs_test_1","client_reference_id":"%d","customer":"cus_test_1",
                 "subscription":"%s"}}}""".formatted(eventId, userId, subId);
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
    void forgedWebhooksAreRejected() throws Exception {
        String payload = checkoutCompleted("evt_forged", 1, "sub_x");
        // Wrong signature
        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Stripe-Signature", "t=1700000000,v1=deadbeef"))
                .andExpect(status().isBadRequest());
        // No signature at all
        mockMvc.perform(post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutCompletedUpgradesToProExactlyOnce() throws Exception {
        String token = register("billing-pro@example.com");
        long uid = userId(token);
        // Accounts now default to PRO; drop to FREE so the checkout upgrade is observable.
        mockMvc.perform(post("/api/billing/demo-downgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals("FREE", planOf(token));

        sendWebhook(checkoutCompleted("evt_up_1", uid, "sub_first"));
        assertEquals("PRO", planOf(token));
        mockMvc.perform(get("/api/billing").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.subscriptionStatus").value("active"));

        // Same event id redelivered with TAMPERED content: swallowed, nothing changes.
        sendWebhook(checkoutCompleted("evt_up_1", uid, "sub_TAMPERED"));
        assertEquals("PRO", planOf(token));
        assertEquals("sub_first", subscriptionRepository.findByUserId(uid)
                .orElseThrow().getStripeSubscriptionId());
    }

    @Test
    void subscriptionLifecycleUpdatesThePlan() throws Exception {
        String token = register("billing-lifecycle@example.com");
        long uid = userId(token);
        sendWebhook(checkoutCompleted("evt_lc_1", uid, "sub_lc"));
        assertEquals("PRO", planOf(token));

        // past_due -> access revoked
        sendWebhook("""
                {"id":"evt_lc_2","type":"customer.subscription.updated","data":{"object":{
                 "id":"sub_lc","status":"past_due","current_period_end":1900000000}}}""");
        assertEquals("FREE", planOf(token));

        // recovered -> PRO again
        sendWebhook("""
                {"id":"evt_lc_3","type":"customer.subscription.updated","data":{"object":{
                 "id":"sub_lc","status":"active","current_period_end":1900000000}}}""");
        assertEquals("PRO", planOf(token));

        // deleted -> FREE, status canceled
        sendWebhook("""
                {"id":"evt_lc_4","type":"customer.subscription.deleted","data":{"object":{
                 "id":"sub_lc"}}}""");
        assertEquals("FREE", planOf(token));
        mockMvc.perform(get("/api/billing").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.subscriptionStatus").value("canceled"));
    }

    @Test
    void downgradeKeepsLivePagesButGatesCreationAndRepublish() throws Exception {
        String token = register("billing-downgrade@example.com");
        long uid = userId(token);
        sendWebhook(checkoutCompleted("evt_dg_1", uid, "sub_dg"));

        // PRO: a second kit is allowed and publishable.
        long kit1 = createKit(token, "Ilk Kit DG");
        long kit2 = createKit(token, "Ikinci Kit DG");
        mockMvc.perform(post("/api/mediakits/" + kit2 + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/ikinci-kit-dg")).andExpect(status().isOk());

        // Downgrade.
        sendWebhook("""
                {"id":"evt_dg_2","type":"customer.subscription.deleted","data":{"object":{
                 "id":"sub_dg"}}}""");
        assertEquals("FREE", planOf(token));

        // The live page SURVIVES (brand links must not break)...
        mockMvc.perform(get("/api/public/kits/ikinci-kit-dg")).andExpect(status().isOk());
        // ...but the excess kit cannot be republished, and no new kit fits.
        mockMvc.perform(post("/api/mediakits/" + kit2 + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ucuncu\"}"))
                .andExpect(status().isForbidden());
        // The oldest kit stays fully usable.
        mockMvc.perform(post("/api/mediakits/" + kit1 + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void badgeIsFrozenByPlanAtPublishTime() throws Exception {
        String token = register("billing-badge@example.com");
        long uid = userId(token);
        // Accounts now default to PRO; drop to FREE so the badge freezes on at publish.
        mockMvc.perform(post("/api/billing/demo-downgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long kitId = createKit(token, "Rozet Kit");

        // FREE publish -> badge on.
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/rozet-kit"))
                .andExpect(jsonPath("$.showBadge").value(true));

        // Upgrade, republish -> badge off in the NEW snapshot.
        sendWebhook(checkoutCompleted("evt_badge_1", uid, "sub_badge"));
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/rozet-kit"))
                .andExpect(jsonPath("$.showBadge").value(false));
    }

    @Test
    void checkoutRequiresAuthAndConfiguredBilling() throws Exception {
        mockMvc.perform(post("/api/billing/checkout"))
                .andExpect(status().isUnauthorized());
        // With the dummy dev key, checkout degrades to 503 instead of calling Stripe.
        String token = register("billing-unconfigured@example.com");
        mockMvc.perform(post("/api/billing/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
    }
}
