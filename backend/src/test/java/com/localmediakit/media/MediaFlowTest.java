package com.localmediakit.media;

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

/**
 * The showcase: the creator's actual work, which the page could not show.
 *
 * <p>Behaves like the rate card and the collaborations, and the important part
 * is the last test -- it freezes into the snapshot, so editing the list does
 * not touch a published page until the next publish. That rule is the product's
 * central promise and every list added to a kit has to keep it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anItemIsCreatedListedEditedAndRemoved() throws Exception {
        String token = register("media-crud@example.com");
        long kitId = createKit(token, "Medya Kiti");

        long itemId = addItem(token, kitId, "En iyi video", "https://youtube.com/watch?v=1");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/media")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("En iyi video"))
                .andExpect(jsonPath("$[0].url").value("https://youtube.com/watch?v=1"));

        mockMvc.perform(put("/api/mediakits/" + kitId + "/media/" + itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Guncellenmis","url":"https://youtube.com/watch?v=2",
                                 "platform":"YOUTUBE","displayOrder":1}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Guncellenmis"))
                .andExpect(jsonPath("$.platform").value("YOUTUBE"));

        mockMvc.perform(delete("/api/mediakits/" + kitId + "/media/" + itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/media")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anHttpUrlIsRefused() throws Exception {
        // The same rule every other URL column uses. A brand opening the page
        // over HTTPS should not meet a mixed-content warning because of a link
        // in the showcase.
        String token = register("media-http@example.com");
        long kitId = createKit(token, "Guvensiz Kit");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/media")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Video\",\"url\":\"http://example.com/v\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aThumbnailIsOptionalAndAnEmptyOneIsNotStoredAsEmpty() throws Exception {
        // "" would put an image element with no source on the public page.
        String token = register("media-thumb@example.com");
        long kitId = createKit(token, "Kapaksiz Kit");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/media")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Kapaksiz","url":"https://example.com/v","thumbnailUrl":""}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.thumbnailUrl").doesNotExist());
    }

    @Test
    void itemsAreOwnerScoped() throws Exception {
        String owner = register("media-owner@example.com");
        String stranger = register("media-stranger@example.com");
        long kitId = createKit(owner, "Sahipli Kit");
        long itemId = addItem(owner, kitId, "Gizli", "https://example.com/gizli");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/media")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/mediakits/" + kitId + "/media/" + itemId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void editingTheShowcaseDoesNotTouchAPublishedPageUntilTheNextPublish() throws Exception {
        // The rule the whole product rests on, checked for the newest list.
        String token = register("media-snapshot@example.com");
        long kitId = createKit(token, "Donmus Kit", "media-donmus-kiti");
        addItem(token, kitId, "Yayindaki video", "https://example.com/yayinda");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        addItem(token, kitId, "Taslaktaki video", "https://example.com/taslak");

        mockMvc.perform(get("/api/public/kits/media-donmus-kiti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].title").value("Yayindaki video"));

        // ...and republishing brings it through.
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/media-donmus-kiti"))
                .andExpect(jsonPath("$.media.length()").value(2));
    }

    /* --- helpers --- */

    private long addItem(String token, long kitId, String title, String url) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits/" + kitId + "/media")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"url\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

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

    private long createKit(String token, String title) throws Exception {
        return createKit(token, title, null);
    }

    private long createKit(String token, String title, String slug) throws Exception {
        String body = slug == null
                ? "{\"title\":\"" + title + "\"}"
                : "{\"title\":\"" + title + "\",\"slug\":\"" + slug + "\"}";
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }
}
