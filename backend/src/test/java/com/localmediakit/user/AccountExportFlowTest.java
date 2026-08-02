package com.localmediakit.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account's own data, as one file.
 *
 * <p>The assertions worth reading are the exclusions. An export is a file that
 * ends up in downloads folders and email attachments, so what it does NOT
 * contain matters more than what it does: nothing derived from the password,
 * no live share tokens, and nothing that identifies a visitor.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountExportFlowTest {

    private static final String BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theExportCarriesTheProfileAndEveryKit() throws Exception {
        String token = register("export-full@example.com");
        long kitId = publishedKit(token, "export-full-kiti");
        addCollab(token, kitId, "Nike");

        JsonNode export = export(token);

        assertThat(export.get("profile").get("email").asText()).isEqualTo("export-full@example.com");
        assertThat(export.get("kits")).hasSize(1);
        JsonNode kit = export.get("kits").get(0);
        assertThat(kit.get("slug").asText()).isEqualTo("export-full-kiti");
        assertThat(kit.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(kit.get("collaborations").get(0).get("brandName").asText()).isEqualTo("Nike");
        assertThat(kit.get("publishedVersions")).hasSize(1);
    }

    @Test
    void nothingDerivedFromThePasswordAppearsAnywhere() throws Exception {
        // The one thing in this schema whose whole value is that it never
        // leaves the database.
        String token = register("export-secret@example.com");
        publishedKit(token, "export-secret-kiti");

        String raw = rawExport(token);

        assertThat(raw).doesNotContain("passwordHash");
        assertThat(raw).doesNotContain("$2a$");
    }

    @Test
    void aBrandsMessageIsExportedButTheVisitorFingerprintIsNot() throws Exception {
        // The message is correspondence addressed to this creator and is
        // theirs. The hash that deduplicated the submission identifies somebody
        // else, and exporting it would be exporting another person's data under
        // the heading of exporting your own.
        String token = register("export-lead@example.com");
        long kitId = publishedKit(token, "export-lead-kiti");
        submitLead("export-lead-kiti", "Adidas", "adidas@example.com", "Teklif", "203.0.113.70");

        JsonNode export = export(token);
        JsonNode lead = export.get("kits").get(0).get("leads").get(0);

        assertThat(lead.get("brandName").asText()).isEqualTo("Adidas");
        assertThat(lead.get("message").asText()).isEqualTo("Teklif");
        assertThat(lead.has("visitorHash")).isFalse();
        assertThat(rawExport(token)).doesNotContain("visitorHash");
    }

    @Test
    void shareLinksAreExportedWithoutTheirTokens() throws Exception {
        // A token in an exported file is a live link sitting in a downloads
        // folder. The label and the count are the useful part.
        String token = register("export-share@example.com");
        long kitId = publishedKit(token, "export-share-kiti");
        String shareToken = createShareLink(token, kitId, "Puma");

        String raw = rawExport(token);

        assertThat(raw).contains("Puma");
        assertThat(raw).doesNotContain(shareToken);
    }

    @Test
    void visitCountsAreExportedButNotTheVisits() throws Exception {
        // The count is a fact about the creator's page. The rows behind it are
        // records about other people who opened it.
        String token = register("export-views@example.com");
        long kitId = publishedKit(token, "export-views-kiti");
        visit("export-views-kiti", "203.0.113.71");

        JsonNode analytics = export(token).get("kits").get(0).get("analytics");

        assertThat(analytics.get("totalViews").asLong()).isEqualTo(1);
        assertThat(rawExport(token)).doesNotContain("pageViews");
    }

    @Test
    void theFileAnnouncesItselfAsADownload() throws Exception {
        String token = register("export-download@example.com");

        mockMvc.perform(get("/api/me/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"localmediakit-export.json\""))
                .andExpect(jsonPath("$.exportedAt").exists());
    }

    @Test
    void anExportOnlyEverContainsTheCallersOwnAccount() throws Exception {
        // There is no identifier in the request, so there is nothing to tamper
        // with -- the subject comes from the token. This pins that.
        String mine = register("export-mine@example.com");
        String theirs = register("export-theirs@example.com");
        publishedKit(theirs, "export-theirs-kiti");

        JsonNode export = export(mine);

        assertThat(export.get("profile").get("email").asText()).isEqualTo("export-mine@example.com");
        assertThat(export.get("kits")).isEmpty();
    }

    /* --- helpers --- */

    private JsonNode export(String token) throws Exception {
        return objectMapper.readTree(rawExport(token));
    }

    private String rawExport(String token) throws Exception {
        return mockMvc.perform(get("/api/me/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void visit(String slug, String ip) throws Exception {
        mockMvc.perform(post("/api/track")
                        .header("User-Agent", BROWSER)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isAccepted());
    }

    private void submitLead(String slug, String brand, String email, String message, String ip)
            throws Exception {
        mockMvc.perform(post("/api/public/kits/" + slug + "/contact")
                        .header("User-Agent", BROWSER)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brandName":"%s","email":"%s","message":"%s"}
                                """.formatted(brand, email, message)))
                .andExpect(status().isAccepted());
    }

    private String createShareLink(String token, long kitId, String label) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits/" + kitId + "/share-links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("token").asText();
    }

    private void addCollab(String token, long kitId, String brand) throws Exception {
        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"" + brand + "\"}"))
                .andExpect(status().isCreated());
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret","displayName":"Disa Aktaran"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long publishedKit(String token, String slug) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Disa Aktarim Kiti\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long kitId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return kitId;
    }
}
