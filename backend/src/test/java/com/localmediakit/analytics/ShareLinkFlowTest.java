package com.localmediakit.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Labelled share links: the half of the product that was missing.
 *
 * <p>The premise was always that a creator sends a brand a link and finds out
 * they read it. Analytics could only ever say "three views", because every
 * visitor is an anonymous hash that rotates daily -- and that has not changed.
 * The label comes from the creator, who knew who they were sending it to.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShareLinkFlowTest {

    private static final String BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aViewThroughALabelledLinkIsAttributedToIt() throws Exception {
        String token = register("share-basic@example.com");
        long kitId = publishedKit(token, "share-basic-kiti");
        String linkToken = createLink(token, kitId, "Nike");

        visit("share-basic-kiti", linkToken, "203.0.113.20");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/share-links")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Nike"))
                .andExpect(jsonPath("$[0].views").value(1))
                .andExpect(jsonPath("$[0].uniqueVisitors").value(1))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void theLinkCarriesTheSlugSoTheDashboardNeverRebuildsIt() throws Exception {
        String token = register("share-url@example.com");
        long kitId = publishedKit(token, "share-url-kiti");
        String linkToken = createLink(token, kitId, "Adidas");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/share-links")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].url").value("/share-url-kiti?r=" + linkToken));
    }

    @Test
    void aVisitWithNoTokenIsStillCountedJustNotAttributed() throws Exception {
        // The ordinary case: a link pasted somewhere public, or one sent before
        // the creator started labelling. Losing the visit to protect a footnote
        // about it would be the wrong way round.
        String token = register("share-plain@example.com");
        long kitId = publishedKit(token, "share-plain-kiti");
        createLink(token, kitId, "Puma");

        visit("share-plain-kiti", null, "203.0.113.21");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalViews").value(1));
        mockMvc.perform(get("/api/mediakits/" + kitId + "/share-links")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].views").value(0));
    }

    @Test
    void aTokenFromAnotherKitCannotMoveAViewIntoIt() throws Exception {
        // The token is not a secret, so somebody else's could turn up on a URL.
        // It must not be able to write into their numbers.
        String owner = register("share-mine@example.com");
        long mine = publishedKit(owner, "share-mine-kiti");
        String stranger = register("share-theirs@example.com");
        long theirs = publishedKit(stranger, "share-theirs-kiti");
        String theirLinkToken = createLink(stranger, theirs, "Baskasinin Markasi");

        visit("share-mine-kiti", theirLinkToken, "203.0.113.22");

        // Counted on the kit that was actually visited...
        mockMvc.perform(get("/api/mediakits/" + mine + "/analytics")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.totalViews").value(1));
        // ...and attributed to nobody.
        mockMvc.perform(get("/api/mediakits/" + theirs + "/share-links")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(jsonPath("$[0].views").value(0));
    }

    @Test
    void aRevokedLinkStopsCollectingButKeepsWhatItAlreadyHas() throws Exception {
        String token = register("share-revoke@example.com");
        long kitId = publishedKit(token, "share-revoke-kiti");
        String linkToken = createLink(token, kitId, "Eski Marka");
        visit("share-revoke-kiti", linkToken, "203.0.113.23");

        long linkId = firstLinkId(token, kitId);
        mockMvc.perform(delete("/api/mediakits/" + kitId + "/share-links/" + linkId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        visit("share-revoke-kiti", linkToken, "203.0.113.24");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/share-links")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].active").value(false))
                // The first visit really did come through this link; rewriting
                // that would make the history a worse record than none.
                .andExpect(jsonPath("$[0].views").value(1));
        // The second visit is still a visit.
        mockMvc.perform(get("/api/mediakits/" + kitId + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalViews").value(2));
    }

    @Test
    void linksAreOwnerScopedInBothDirections() throws Exception {
        String owner = register("share-owner@example.com");
        String stranger = register("share-stranger@example.com");
        long kitId = publishedKit(owner, "share-owner-kiti");
        createLink(owner, kitId, "Gizli Marka");
        long linkId = firstLinkId(owner, kitId);

        mockMvc.perform(get("/api/mediakits/" + kitId + "/share-links")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/mediakits/" + kitId + "/share-links/" + linkId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void aLabelIsRequired() throws Exception {
        // An unnamed share link has had its only reason for existing left blank.
        String token = register("share-unlabelled@example.com");
        long kitId = publishedKit(token, "share-unlabelled-kiti");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/share-links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoLinksNeverShareAToken() throws Exception {
        String token = register("share-unique@example.com");
        long kitId = publishedKit(token, "share-unique-kiti");

        String first = createLink(token, kitId, "Marka A");
        String second = createLink(token, kitId, "Marka B");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).hasSizeGreaterThanOrEqualTo(20);
    }

    /* --- helpers --- */

    private void visit(String slug, String shareToken, String ip) throws Exception {
        String body = shareToken == null
                ? "{\"slug\":\"%s\"}".formatted(slug)
                : "{\"slug\":\"%s\",\"shareToken\":\"%s\"}".formatted(slug, shareToken);
        mockMvc.perform(post("/api/track")
                        .header("User-Agent", BROWSER)
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
    }

    private String createLink(String token, long kitId, String label) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits/" + kitId + "/share-links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("token").asText();
    }

    private long firstLinkId(String token, long kitId) throws Exception {
        String list = mockMvc.perform(get("/api/mediakits/" + kitId + "/share-links")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(list).get(0).get("id").asLong();
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret","displayName":"Paylasan"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long publishedKit(String token, String slug) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Paylasim Kiti\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long kitId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return kitId;
    }
}
