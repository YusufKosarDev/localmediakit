package com.localmediakit.mediakit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PreviewFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Previewer"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createKit(String token, String json) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private String previewToken(String token, long kitId) throws Exception {
        String response = mockMvc.perform(post("/api/mediakits/" + kitId + "/preview-link")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void ownerCanPreviewAnUnpublishedDraft() throws Exception {
        String token = register("prev-draft@example.com");
        long kitId = createKit(token, "{\"title\":\"Taslak Kit\",\"headline\":\"Hic yayinlanmadi\"}");

        // Nothing public exists yet...
        mockMvc.perform(get("/api/public/kits/taslak-kit"))
                .andExpect(status().isNotFound());

        // ...but the preview link renders the draft with the full public shape.
        String preview = previewToken(token, kitId);
        mockMvc.perform(get("/api/public/preview/" + preview))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Taslak Kit"))
                .andExpect(jsonPath("$.headline").value("Hic yayinlanmadi"))
                .andExpect(jsonPath("$.displayName").value("Previewer"))
                .andExpect(jsonPath("$.isProtected").value(false))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void previewShowsDraftEditsWhilePublicPageStaysFrozen() throws Exception {
        String token = register("prev-frozen@example.com");
        long kitId = createKit(token, "{\"title\":\"Canli Kit\",\"headline\":\"Yayinlanan\"}");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Canli Kit\",\"headline\":\"Taslakta yeni\"}"))
                .andExpect(status().isOk());

        // The published snapshot is untouched; the preview follows the draft.
        mockMvc.perform(get("/api/public/kits/canli-kit"))
                .andExpect(jsonPath("$.headline").value("Yayinlanan"));
        mockMvc.perform(get("/api/public/preview/" + previewToken(token, kitId)))
                .andExpect(jsonPath("$.headline").value("Taslakta yeni"));
    }

    @Test
    void previewLinkMintingIsOwnerScopedAndAuthenticated() throws Exception {
        String owner = register("prev-owner@example.com");
        String stranger = register("prev-stranger@example.com");
        long kitId = createKit(owner, "{\"title\":\"Korunan Taslak\"}");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/preview-link"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/mediakits/" + kitId + "/preview-link")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTokensAllCollapseIntoTheSame404() throws Exception {
        String token = register("prev-badtoken@example.com");
        createKit(token, "{\"title\":\"Herhangi\"}");

        // Garbage.
        mockMvc.perform(get("/api/public/preview/not-a-jwt"))
                .andExpect(status().isNotFound());
        // A SESSION token is not a preview token, even though its signature is valid.
        mockMvc.perform(get("/api/public/preview/" + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewTokenCannotBeUsedAsASession() throws Exception {
        String token = register("prev-noescalate@example.com");
        long kitId = createKit(token, "{\"title\":\"Yetki Testi\"}");
        String preview = previewToken(token, kitId);

        // The preview token must never authenticate an API session.
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + preview))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mediakits")
                        .header("Authorization", "Bearer " + preview))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void previewOfAPasswordProtectedKitSkipsTheGate() throws Exception {
        String token = register("prev-protected@example.com");
        // Password protection is PRO: use the demo upgrade (Stripe unconfigured in tests).
        mockMvc.perform(post("/api/billing/demo-upgrade")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long kitId = createKit(token, "{\"title\":\"Gizli Kit\",\"headline\":\"Sifreli icerik\"}");
        mockMvc.perform(put("/api/mediakits/" + kitId + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"kitpass\"}"))
                .andExpect(status().isNoContent());

        // The token IS the authorization: full content, no password prompt.
        mockMvc.perform(get("/api/public/preview/" + previewToken(token, kitId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isProtected").value(false))
                .andExpect(jsonPath("$.headline").value("Sifreli icerik"));
    }

    @Test
    void previewOfADeletedKitIs404() throws Exception {
        String token = register("prev-deleted@example.com");
        long kitId = createKit(token, "{\"title\":\"Silinecek Taslak\"}");
        String preview = previewToken(token, kitId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/public/preview/" + preview))
                .andExpect(status().isNotFound());
    }
}
