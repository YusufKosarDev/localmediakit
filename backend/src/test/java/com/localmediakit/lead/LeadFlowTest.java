package com.localmediakit.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LeadFlowTest {

    /** A believable browser UA (the bot filter drops absent/bot-like agents). */
    private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Uretici"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createAndPublish(String token, String title) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long kitId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return kitId;
    }

    private void submit(String slug, String brand, String ua) throws Exception {
        mockMvc.perform(post("/api/public/kits/" + slug + "/contact")
                        .header("User-Agent", ua)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"" + brand + "\",\"email\":\"marka@ornek.com\",\"message\":\"Isbirligi teklifi\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void submittedLeadLandsInTheOwnersInbox() throws Exception {
        String token = register("lead-basic@example.com");
        long kitId = createAndPublish(token, "Teklif Kutusu");

        submit("teklif-kutusu", "Marka AS", BROWSER_UA);

        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].brandName").value("Marka AS"))
                .andExpect(jsonPath("$[0].email").value("marka@ornek.com"))
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void honeypotBotsAndUnknownSlugsAreSilentlyDropped() throws Exception {
        String token = register("lead-drop@example.com");
        long kitId = createAndPublish(token, "Sessiz Kutu");

        // Honeypot filled: a real browser never does this.
        mockMvc.perform(post("/api/public/kits/sessiz-kutu/contact")
                        .header("User-Agent", BROWSER_UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"Bot\",\"email\":\"bot@spam.com\",\"message\":\"spam\",\"website\":\"http://spam.example\"}"))
                .andExpect(status().isAccepted());
        // Bot user agent.
        mockMvc.perform(post("/api/public/kits/sessiz-kutu/contact")
                        .header("User-Agent", "curl/8.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"Curl\",\"email\":\"c@c.com\",\"message\":\"hi\"}"))
                .andExpect(status().isAccepted());
        // Unknown slug: same 202, nothing stored, existence not leaked.
        mockMvc.perform(post("/api/public/kits/boyle-bir-kit-yok/contact")
                        .header("User-Agent", BROWSER_UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"Kayip\",\"email\":\"k@k.com\",\"message\":\"merhaba\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void disablingContactStopsIngestionImmediatelyButThePublishedPageStaysFrozen() throws Exception {
        String token = register("lead-kill@example.com");
        long kitId = createAndPublish(token, "Kapali Kutu");

        // Published with contact enabled -> frozen into the public payload.
        mockMvc.perform(get("/api/public/kits/kapali-kutu"))
                .andExpect(jsonPath("$.contactEnabled").value(true));

        // Kill switch: disable on the draft (no republish).
        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Kapali Kutu\",\"contactEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEnabled").value(false));

        // The frozen page still SHOWS the form...
        mockMvc.perform(get("/api/public/kits/kapali-kutu"))
                .andExpect(jsonPath("$.contactEnabled").value(true));
        // ...but ingestion is already off.
        submit("kapali-kutu", "Gec Kalan Marka", BROWSER_UA);
        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));

        // Republish freezes the OFF state into the public page.
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/kapali-kutu"))
                .andExpect(jsonPath("$.contactEnabled").value(false));
    }

    @Test
    void perVisitorWindowCapDropsTheFourthSubmission() throws Exception {
        String token = register("lead-cap@example.com");
        long kitId = createAndPublish(token, "Limitli Kutu");

        for (int i = 1; i <= 4; i++) {
            submit("limitli-kutu", "Israrci Marka " + i, BROWSER_UA);
        }
        // Same visitor (same IP+UA+day): only the first 3 within the window count.
        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void freeInboxShowsOnlyTheMostRecentLeadsUntilPro() throws Exception {
        String token = register("lead-plan@example.com");
        // Accounts now default to PRO; drop to FREE to exercise the capped view.
        mockMvc.perform(post("/api/billing/demo-downgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long kitId = createAndPublish(token, "Dolu Kutu");

        // 12 leads from 12 distinct visitors (distinct UA => distinct fingerprint).
        for (int i = 1; i <= 12; i++) {
            submit("dolu-kutu", "Marka " + i, BROWSER_UA + " Visitor/" + i);
        }

        // FREE: view capped at the 10 most recent; ingestion was never capped.
        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(10));

        // PRO (demo upgrade): the full inbox becomes visible.
        mockMvc.perform(post("/api/billing/demo-upgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(12));
    }

    @Test
    void ownerManagesLeadLifecycle() throws Exception {
        String token = register("lead-lifecycle@example.com");
        long kitId = createAndPublish(token, "Yasam Dongusu");
        submit("yasam-dongusu", "Marka AS", BROWSER_UA);

        String listJson = mockMvc.perform(get("/api/mediakits/" + kitId + "/leads")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        long leadId = objectMapper.readTree(listJson).get(0).get("id").asLong();

        mockMvc.perform(put("/api/mediakits/" + kitId + "/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));

        mockMvc.perform(delete("/api/mediakits/" + kitId + "/leads/" + leadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void inboxIsOwnerScopedAndAuthenticated() throws Exception {
        String owner = register("lead-owner@example.com");
        String stranger = register("lead-stranger@example.com");
        long kitId = createAndPublish(owner, "Ozel Kutu");
        submit("ozel-kutu", "Marka AS", BROWSER_UA);

        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedSubmissionIsRejectedWith400() throws Exception {
        // Validation failures are a caller bug, not an enumeration surface: 400.
        mockMvc.perform(post("/api/public/kits/herhangi/contact")
                        .header("User-Agent", BROWSER_UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"\",\"email\":\"gecersiz\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
