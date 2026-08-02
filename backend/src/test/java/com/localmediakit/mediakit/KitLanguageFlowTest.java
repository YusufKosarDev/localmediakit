package com.localmediakit.mediakit;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A kit's presentation language.
 *
 * <p>The decision this pins down: the public page's language comes from the
 * published snapshot, not from the visitor. A brand representative opening the
 * link is not signed in and has no preference the server could honour, and
 * varying by Accept-Language would mean the page could no longer be one
 * static edge-cached entry per URL.
 */
@SpringBootTest
@AutoConfigureMockMvc
class KitLanguageFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret","displayName":"Uretici"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createKit(String token, String slug, String language) throws Exception {
        String body = language == null
                ? """
                  {"title":"Kit","slug":"%s"}
                  """.formatted(slug)
                : """
                  {"title":"Kit","slug":"%s","language":"%s"}
                  """.formatted(slug, language);
        String response = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void aNewKitStartsInTheOwnersDashboardLanguage() throws Exception {
        String token = register("varsayilandil@example.com");

        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Uretici","locale":"en"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en"));

        // A sensible starting point — but still the kit's own field, so it can
        // be changed per kit afterwards.
        long kitId = createKit(token, "dil-varsayilan", null);
        mockMvc.perform(get("/api/mediakits/" + kitId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.language").value("en"));
    }

    @Test
    void oneCreatorCanRunKitsInDifferentLanguagesAtOnce() throws Exception {
        String token = register("ikidil@example.com");
        long turkish = createKit(token, "dil-turkce", "tr");
        long english = createKit(token, "dil-ingilizce", "en");

        mockMvc.perform(post("/api/mediakits/" + turkish + "/publish")
                .header("Authorization", "Bearer " + token));
        mockMvc.perform(post("/api/mediakits/" + english + "/publish")
                .header("Authorization", "Bearer " + token));

        // The point of putting language on the kit rather than the account:
        // pitching Turkish brands and international ones at the same time.
        mockMvc.perform(get("/api/public/kits/dil-turkce"))
                .andExpect(jsonPath("$.language").value("tr"));
        mockMvc.perform(get("/api/public/kits/dil-ingilizce"))
                .andExpect(jsonPath("$.language").value("en"));
    }

    @Test
    void anUnsupportedLanguageIsRefused() throws Exception {
        String token = register("gecersizdil@example.com");

        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Kit","slug":"dil-gecersiz","language":"klingon"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingTheDraftsLanguageDoesNotTouchThePublishedPage() throws Exception {
        String token = register("dildonma@example.com");
        long kitId = createKit(token, "dil-donma", "tr");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                .header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/public/kits/dil-donma"))
                .andExpect(jsonPath("$.language").value("tr"));

        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Kit","language":"en","slug":"dil-donma"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"));

        // Frozen: a brand reading the page sees the language it was published
        // in, not whatever the draft says now.
        mockMvc.perform(get("/api/public/kits/dil-donma"))
                .andExpect(jsonPath("$.language").value("tr"));

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                .header("Authorization", "Bearer " + token));
        mockMvc.perform(get("/api/public/kits/dil-donma"))
                .andExpect(jsonPath("$.language").value("en"));
    }

    /** Snapshots taken before i18n have no language field at all. */
    @Test
    void aSnapshotFromBeforeI18nRendersInTurkish() {
        MediaKitSnapshot legacy = new MediaKitSnapshot(
                "eski-kit", "Eski Kit", null, null,
                "light", null, null, null,
                "Uretici", null, null, null, null, null, null, null);

        assertThat(legacy.languageOrDefault()).isEqualTo("tr");
    }

    @Test
    void theAccountLocaleIsRejectedWhenUnsupported() throws Exception {
        String token = register("hesapdil@example.com");

        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Uretici","locale":"klingon"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
