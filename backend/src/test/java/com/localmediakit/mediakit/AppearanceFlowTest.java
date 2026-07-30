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
 * Per-kit appearance: the curated accent and layout, and the rule that binds
 * them to everything else — a look only reaches the public page by publishing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppearanceFlowTest {

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

    private long createKit(String token, String slug, String accent, String layout) throws Exception {
        String body = accent == null
                ? """
                  {"title":"Kit","slug":"%s"}
                  """.formatted(slug)
                : """
                  {"title":"Kit","slug":"%s","accent":"%s","layout":"%s"}
                  """.formatted(slug, accent, layout);
        String response = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void aNewKitGetsTheOriginalLook() throws Exception {
        String token = register("varsayilan@example.com");
        long kitId = createKit(token, "gorunum-varsayilan", null, null);

        // The defaults are exactly what every kit looked like before accents
        // existed, so adding this feature changed nobody's page.
        mockMvc.perform(get("/api/mediakits/" + kitId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.theme").value("light"))
                .andExpect(jsonPath("$.accent").value("violet"))
                .andExpect(jsonPath("$.layout").value("classic"));
    }

    @Test
    void anAccentOutsideTheCuratedSetIsRefused() throws Exception {
        String token = register("gecersiz@example.com");

        // The whole accessibility guarantee rests on the set being closed:
        // arbitrary colours would let someone publish unreadable text.
        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Kit","slug":"gecersiz-accent","accent":"#ff0000"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Kit","slug":"gecersiz-layout","layout":"karmasik"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void appearanceIsCaseInsensitiveAndTrimmed() throws Exception {
        String token = register("normalize@example.com");
        long kitId = createKit(token, "gorunum-normalize", "OCEAN", "Panel");

        mockMvc.perform(get("/api/mediakits/" + kitId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.accent").value("ocean"))
                .andExpect(jsonPath("$.layout").value("panel"));
    }

    /** The rule this whole architecture exists for. */
    @Test
    void changingTheLookInTheDraftDoesNotTouchThePublishedPage() throws Exception {
        String token = register("donma@example.com");
        long kitId = createKit(token, "gorunum-donma", "ocean", "classic");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/kits/gorunum-donma"))
                .andExpect(jsonPath("$.accent").value("ocean"))
                .andExpect(jsonPath("$.layout").value("classic"));

        // Change the draft's look — and only the draft's.
        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Kit","accent":"rose","layout":"panel","slug":"gorunum-donma"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accent").value("rose"));

        // The published snapshot is untouched: a brand looking at the page
        // still sees the look that was frozen at publish.
        mockMvc.perform(get("/api/public/kits/gorunum-donma"))
                .andExpect(jsonPath("$.accent").value("ocean"))
                .andExpect(jsonPath("$.layout").value("classic"));

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/kits/gorunum-donma"))
                .andExpect(jsonPath("$.accent").value("rose"))
                .andExpect(jsonPath("$.layout").value("panel"));
    }

    /**
     * Snapshots taken before these fields existed have no accent or layout at
     * all. They must keep rendering as the original look rather than as
     * something unstyled.
     */
    @Test
    void aSnapshotFromBeforeAppearanceExistedStillRendersTheOriginalLook() throws Exception {
        MediaKitSnapshot legacy = new MediaKitSnapshot(
                "eski-kit", "Eski Kit", "Basliik", null,
                "light", null, null, null,
                "Uretici", null, null, null, null, null, null);

        assertThat(legacy.accentOrDefault()).isEqualTo(KitAppearance.DEFAULT_ACCENT);
        assertThat(legacy.layoutOrDefault()).isEqualTo(KitAppearance.DEFAULT_LAYOUT);
    }

    @Test
    void blankAppearanceFallsBackInsteadOfBeingRejected() throws Exception {
        String token = register("bos@example.com");
        // Older clients omit the fields entirely; that must not be an error.
        long kitId = createKit(token, "gorunum-bos", "", "");

        mockMvc.perform(get("/api/mediakits/" + kitId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.accent").value("violet"))
                .andExpect(jsonPath("$.layout").value("classic"));
    }

    @Test
    void theVersionDiffReportsAnAppearanceChange() throws Exception {
        String token = register("diff@example.com");
        long kitId = createKit(token, "gorunum-diff", "ocean", "classic");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                .header("Authorization", "Bearer " + token));

        mockMvc.perform(put("/api/mediakits/" + kitId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Kit","accent":"forest","layout":"panel","slug":"gorunum-diff"}
                        """));
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                .header("Authorization", "Bearer " + token));

        String diff = mockMvc.perform(get("/api/mediakits/" + kitId + "/versions/diff?from=1&to=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(diff).contains("accent").contains("forest");
        assertThat(diff).contains("layout").contains("panel");
    }
}
