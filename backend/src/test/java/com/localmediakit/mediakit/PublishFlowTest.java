package com.localmediakit.mediakit;

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
class PublishFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Publisher"}
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

    private void publish(String token, long kitId) throws Exception {
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void publishFreezesSnapshotAndServesItPublicly() throws Exception {
        String token = register("pub-basic@example.com");
        long kitId = createKit(token, "{\"title\":\"Kanal Bir\",\"headline\":\"Ilk hali\"}");

        // Draft only: nothing public yet.
        mockMvc.perform(get("/api/public/kits/kanal-bir"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.slug").value("kanal-bir"));

        mockMvc.perform(get("/api/public/kits/kanal-bir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Kanal Bir"))
                .andExpect(jsonPath("$.headline").value("Ilk hali"))
                .andExpect(jsonPath("$.displayName").value("Publisher"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedSlug").value("kanal-bir"));
    }

    @Test
    void draftEditsDoNotLeakIntoThePublishedSnapshot() throws Exception {
        String token = register("pub-immutable@example.com");
        long kitId = createKit(token, "{\"title\":\"Sabit Kit\",\"headline\":\"Yayinlanan\"}");
        publish(token, kitId);

        // Edit the draft WITHOUT publishing.
        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sabit Kit\",\"headline\":\"Taslakta degisti\"}"))
                .andExpect(status().isOk());

        // Public snapshot is immutable: still the published content.
        mockMvc.perform(get("/api/public/kits/sabit-kit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Yayinlanan"));
    }

    @Test
    void republishCreatesNewVersionAndRollbackRestoresOldOne() throws Exception {
        String token = register("pub-versions@example.com");
        long kitId = createKit(token, "{\"title\":\"Versiyonlu\",\"headline\":\"v1 icerik\"}");
        publish(token, kitId);

        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Versiyonlu\",\"headline\":\"v2 icerik\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/public/kits/versiyonlu"))
                .andExpect(jsonPath("$.headline").value("v2 icerik"));

        // Version history, newest first, v2 active.
        mockMvc.perform(get("/api/mediakits/" + kitId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].version").value(1))
                .andExpect(jsonPath("$[1].active").value(false));

        // Rollback to v1: the old immutable snapshot goes live again.
        mockMvc.perform(post("/api/mediakits/" + kitId + "/versions/1/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/public/kits/versiyonlu"))
                .andExpect(jsonPath("$.headline").value("v1 icerik"));
    }

    @Test
    void slugRenameMovesPublicUrlOnlyAfterRepublish() throws Exception {
        String token = register("pub-rename@example.com");
        long kitId = createKit(token, "{\"title\":\"Eski Ad\"}");
        publish(token, kitId);

        // Rename the draft slug; the public page must stay at the old URL.
        mockMvc.perform(put("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Eski Ad\",\"slug\":\"yeni-ad\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/kits/eski-ad")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/yeni-ad")).andExpect(status().isNotFound());

        // Republish: the URL moves and the old one stops serving.
        publish(token, kitId);
        mockMvc.perform(get("/api/public/kits/yeni-ad")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/eski-ad")).andExpect(status().isNotFound());
    }

    @Test
    void livePublishedSlugCannotBeClaimedByAnotherKit() throws Exception {
        String tokenA = register("pub-claim-a@example.com");
        long kitA = createKit(tokenA, "{\"title\":\"Isgal Testi\"}");
        publish(tokenA, kitA);

        // A renames its draft away; the LIVE page still occupies 'isgal-testi'.
        mockMvc.perform(put("/api/mediakits/" + kitA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Isgal Testi\",\"slug\":\"baska-yere-tasindi\"}"))
                .andExpect(status().isOk());

        // B cannot take over the live URL: gets a suffixed slug instead.
        String tokenB = register("pub-claim-b@example.com");
        mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"slug\":\"isgal-testi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("isgal-testi-2"));
    }

    @Test
    void publishIsOwnerScopedAndAuthenticated() throws Exception {
        String owner = register("pub-owner@example.com");
        String stranger = register("pub-stranger@example.com");
        long kitId = createKit(owner, "{\"title\":\"Korunan\"}");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/mediakits/" + kitId + "/versions/1/activate")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void activatingMissingVersionIs404() throws Exception {
        String token = register("pub-noversion@example.com");
        long kitId = createKit(token, "{\"title\":\"Bos Gecmis\"}");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/versions/7/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAKitRemovesItsPublicPage() throws Exception {
        String token = register("pub-delete@example.com");
        long kitId = createKit(token, "{\"title\":\"Silinecek\"}");
        publish(token, kitId);
        mockMvc.perform(get("/api/public/kits/silinecek")).andExpect(status().isOk());

        mockMvc.perform(delete("/api/mediakits/" + kitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/public/kits/silinecek")).andExpect(status().isNotFound());
    }

    @Test
    void statsAreFrozenIntoTheSnapshotAtPublishTime() throws Exception {
        String token = register("pub-stats@example.com");
        long kitId = createKit(token, "{\"title\":\"Istatistikli Kit\"}");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"INSTAGRAM\",\"followers\":1000,\"avgLikes\":50,\"avgComments\":15}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/mediakits/" + kitId + "/demographics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"category\":\"AGE\",\"label\":\"18-24\",\"percentage\":45}]}"))
                .andExpect(status().isOk());
        publish(token, kitId);

        mockMvc.perform(get("/api/public/kits/istatistikli-kit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platforms.length()").value(1))
                .andExpect(jsonPath("$.platforms[0].platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.platforms[0].followers").value(1000))
                .andExpect(jsonPath("$.platforms[0].engagementRate").value(6.50))
                .andExpect(jsonPath("$.demographics[0].label").value("18-24"));

        // New measurements AFTER publish must not move the public page.
        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"INSTAGRAM\",\"followers\":9999,\"avgLikes\":500,\"avgComments\":100}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/public/kits/istatistikli-kit"))
                .andExpect(jsonPath("$.platforms[0].followers").value(1000));

        // Re-publishing freezes the new numbers into a new snapshot.
        publish(token, kitId);
        mockMvc.perform(get("/api/public/kits/istatistikli-kit"))
                .andExpect(jsonPath("$.platforms[0].followers").value(9999));
    }

    @Test
    void collaborationsAreFrozenIntoTheSnapshotAtPublishTime() throws Exception {
        String token = register("pub-collab@example.com");
        long kitId = createKit(token, "{\"title\":\"Markali Kit\"}");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"Ikinci Marka\",\"displayOrder\":1}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"Birinci Marka\",\"displayOrder\":0}"))
                .andExpect(status().isCreated());
        publish(token, kitId);

        // Snapshot array order = display_order, not insertion order.
        mockMvc.perform(get("/api/public/kits/markali-kit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collaborations.length()").value(2))
                .andExpect(jsonPath("$.collaborations[0].brandName").value("Birinci Marka"))
                .andExpect(jsonPath("$.collaborations[1].brandName").value("Ikinci Marka"));

        // A collab added AFTER publish must not appear until re-publish.
        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"Sizinti Marka\",\"displayOrder\":2}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/public/kits/markali-kit"))
                .andExpect(jsonPath("$.collaborations.length()").value(2));

        publish(token, kitId);
        mockMvc.perform(get("/api/public/kits/markali-kit"))
                .andExpect(jsonPath("$.collaborations.length()").value(3));
    }

    @Test
    void seededDemoKitIsServedFromTheNewModel() throws Exception {
        mockMvc.perform(get("/api/public/kits/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Demo Medya Kiti"))
                .andExpect(jsonPath("$.displayName").value("LocalMediaKit"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.platforms.length()").value(3))
                .andExpect(jsonPath("$.platforms[?(@.platform=='YOUTUBE')].engagementRate").value(8.00))
                .andExpect(jsonPath("$.platforms[?(@.platform=='TIKTOK')].followerGrowth30d").value(25.0))
                .andExpect(jsonPath("$.demographics.length()").value(10))
                .andExpect(jsonPath("$.collaborations.length()").value(3))
                .andExpect(jsonPath("$.collaborations[0].brandName").value("Kahve Diyari"));
    }
}
