package com.localmediakit.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exporting the inbox.
 *
 * <p>The content is not the creator's: anyone can fill in the public contact
 * form, so every exported field is a stranger's text, arriving in a file the
 * creator opens in a spreadsheet. That is the whole reason this has its own
 * test rather than being a one-line join.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeadExportFlowTest {

    private static final String BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theExportCarriesTheInboxWithAHeaderRow() throws Exception {
        String token = register("export-basic@example.com");
        String slug = "export-basic-kiti";
        long kitId = publishedKit(token, slug);
        submitLead(slug, "Nike", "nike@example.com", "Isbirligi teklifi", "203.0.113.60");

        String csv = export(token, kitId);

        assertThat(csv).contains("createdAt,brandName,email,status,message");
        assertThat(csv).contains("Nike");
        assertThat(csv).contains("nike@example.com");
        assertThat(csv).contains("Isbirligi teklifi");
    }

    @Test
    void aBrandNameThatIsAFormulaArrivesAsText() throws Exception {
        // The attack: a visitor submits =cmd|... and the creator opens the file
        // in Excel. Quoting alone would not help -- the parser strips quotes
        // before the cell is interpreted.
        String token = register("export-formula@example.com");
        String slug = "export-formula-kiti";
        long kitId = publishedKit(token, slug);
        submitLead(slug, "=1+1", "attacker@example.com", "=HYPERLINK(\"http://evil\")", "203.0.113.61");

        String csv = export(token, kitId);

        assertThat(csv).doesNotContain(",=1+1");
        assertThat(csv).contains("'=1+1");
        assertThat(csv).contains("'=HYPERLINK");
    }

    @Test
    void aMessageWithCommasAndNewlinesKeepsItsColumns() throws Exception {
        String token = register("export-quoting@example.com");
        String slug = "export-quoting-kiti";
        long kitId = publishedKit(token, slug);
        submitLead(slug, "Ajans", "ajans@example.com", "Merhaba,\nikinci satir", "203.0.113.62");

        String csv = export(token, kitId);

        // Two data lines would mean the newline broke out of its cell; the
        // header plus one quoted record is one record.
        assertThat(csv).contains("\"Merhaba,\nikinci satir\"");
    }

    @Test
    void theFileAnnouncesItselfAsADownloadAndAsUtf8() throws Exception {
        String token = register("export-headers@example.com");
        long kitId = publishedKit(token, "export-headers-kiti");

        byte[] body = mockMvc.perform(get("/api/mediakits/" + kitId + "/leads/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"leads-" + kitId + ".csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        // Without the BOM a spreadsheet opens this as the local codepage and
        // every Turkish character in it arrives broken.
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    void anotherOwnersInboxCannotBeExported() throws Exception {
        String owner = register("export-owner@example.com");
        String stranger = register("export-stranger@example.com");
        long kitId = publishedKit(owner, "export-owner-kiti");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/leads/export")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    /* --- helpers --- */

    private String export(String token, long kitId) throws Exception {
        byte[] body = mockMvc.perform(get("/api/mediakits/" + kitId + "/leads/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        // Drop the BOM so the assertions are about the content.
        return new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
    }

    private void submitLead(String slug, String brand, String email, String message, String ip)
            throws Exception {
        mockMvc.perform(post("/api/public/kits/" + slug + "/contact")
                        .header("User-Agent", BROWSER)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ContactRequest(brand, email, message, null))))
                .andExpect(status().isAccepted());
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

    /** @return the new kit's id; the slug is what the caller passed in. */
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
