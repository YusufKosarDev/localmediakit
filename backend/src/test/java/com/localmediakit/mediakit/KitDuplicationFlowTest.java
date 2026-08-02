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
 * Duplicating a kit, so a creator can tailor one per brand without retyping it.
 *
 * <p>The rule under test is the one sentence the service is built around: a
 * copy inherits everything that describes the creator, and nothing that
 * describes the original's own life. Most of these assertions are about the
 * second half -- what must NOT come across -- because that is the half where
 * getting it wrong puts a second live page on the internet or hands somebody
 * else's leads to a new kit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class KitDuplicationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theCopyInheritsTheContentThatDescribesTheCreator() throws Exception {
        String token = register("dup-content@example.com");
        long source = createKit(token, "Kaynak Kit", "dup-kaynak-kiti");
        addStat(token, source, "YOUTUBE", 1000);
        addCollab(token, source, "Nike");
        addRate(token, source, "Reels");
        addMedia(token, source, "En iyi video");

        long copy = duplicate(token, source, "Kaynak Kit (kopya)");

        mockMvc.perform(get("/api/mediakits/" + copy + "/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].platform").value("YOUTUBE"))
                .andExpect(jsonPath("$[0].followers").value(1000));
        mockMvc.perform(get("/api/mediakits/" + copy + "/collaborations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].brandName").value("Nike"));
        mockMvc.perform(get("/api/mediakits/" + copy + "/ratecard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].serviceName").value("Reels"));
        mockMvc.perform(get("/api/mediakits/" + copy + "/media")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].title").value("En iyi video"));
    }

    @Test
    void theCopyIsADraftEvenWhenTheSourceIsLive() throws Exception {
        // Duplicating something and finding a second live page a moment later
        // is exactly the surprise the draft/publish split exists to prevent.
        String token = register("dup-draft@example.com");
        long source = createKit(token, "Yayindaki Kit", "dup-yayindaki-kiti");
        mockMvc.perform(post("/api/mediakits/" + source + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long copy = duplicate(token, source, "Kopya");

        mockMvc.perform(get("/api/mediakits/" + copy)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedSlug").doesNotExist());
    }

    @Test
    void theCopyNeverTakesTheOriginalsAddress() throws Exception {
        String token = register("dup-slug@example.com");
        long source = createKit(token, "Ayni Baslik", "dup-ayni-baslik");

        long copy = duplicate(token, source, "Ayni Baslik");

        String copyJson = mockMvc.perform(get("/api/mediakits/" + copy)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(copyJson).get("slug").asText())
                .isNotEqualTo("dup-ayni-baslik");
    }

    @Test
    void thePasswordDoesNotComeAcross() throws Exception {
        // A password protects a page that has been shared. The copy has not
        // been, and inheriting a secret the creator may not remember setting
        // would lock them out of their own draft.
        String token = register("dup-password@example.com");
        long source = createKit(token, "Sifreli Kit", "dup-sifreli-kiti");
        mockMvc.perform(put("/api/mediakits/" + source + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"gizli123\"}"))
                .andExpect(status().isNoContent());

        long copy = duplicate(token, source, "Kopya");

        mockMvc.perform(get("/api/mediakits/" + copy)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.passwordProtected").value(false));
    }

    @Test
    void theCopyStartsWithNoAudienceOfItsOwn() throws Exception {
        // Views, leads and share links belong to a page that has been out in
        // the world. Copying them would credit the new kit with an audience it
        // never had.
        String token = register("dup-audience@example.com");
        long source = createKit(token, "Ziyaretli Kit", "dup-ziyaretli-kiti");
        mockMvc.perform(post("/api/mediakits/" + source + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/track")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0")
                        .header("X-Forwarded-For", "203.0.113.40")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"dup-ziyaretli-kiti\"}"))
                .andExpect(status().isAccepted());

        long copy = duplicate(token, source, "Kopya");

        mockMvc.perform(get("/api/mediakits/" + copy + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalViews").value(0));
        mockMvc.perform(get("/api/mediakits/" + copy + "/leads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/mediakits/" + copy + "/share-links")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void editingTheCopyLeavesTheOriginalAlone() throws Exception {
        // The rows are copies, not shared references. Getting this wrong would
        // mean editing a draft silently rewrote a live page.
        String token = register("dup-independent@example.com");
        long source = createKit(token, "Bagimsiz Kaynak", "dup-bagimsiz-kaynak");
        addCollab(token, source, "Ilk Marka");
        long copy = duplicate(token, source, "Bagimsiz Kopya");

        String copyCollabs = mockMvc.perform(get("/api/mediakits/" + copy + "/collaborations")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        long collabId = objectMapper.readTree(copyCollabs).get(0).get("id").asLong();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/mediakits/" + copy + "/collaborations/" + collabId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/mediakits/" + source + "/collaborations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anotherOwnersKitCannotBeDuplicated() throws Exception {
        String owner = register("dup-owner@example.com");
        String stranger = register("dup-stranger@example.com");
        long source = createKit(owner, "Sahipli Kit", "dup-sahipli-kiti");

        mockMvc.perform(post("/api/mediakits/" + source + "/duplicate")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Calinti\"}"))
                .andExpect(status().isNotFound());
    }

    /* --- helpers --- */

    private long duplicate(String token, long sourceId, String title) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits/" + sourceId + "/duplicate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private void addStat(String token, long kitId, String platform, int followers) throws Exception {
        mockMvc.perform(post("/api/mediakits/" + kitId + "/stats")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"" + platform + "\",\"followers\":" + followers + "}"))
                .andExpect(status().isCreated());
    }

    private void addCollab(String token, long kitId, String brand) throws Exception {
        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"" + brand + "\"}"))
                .andExpect(status().isCreated());
    }

    private void addRate(String token, long kitId, String service) throws Exception {
        mockMvc.perform(post("/api/mediakits/" + kitId + "/ratecard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"" + service + "\",\"priceAmount\":1000}"))
                .andExpect(status().isCreated());
    }

    private void addMedia(String token, long kitId, String title) throws Exception {
        mockMvc.perform(post("/api/mediakits/" + kitId + "/media")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"url\":\"https://example.com/v\"}"))
                .andExpect(status().isCreated());
    }

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret","displayName":"Kopyalayan"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createKit(String token, String title, String slug) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }
}
