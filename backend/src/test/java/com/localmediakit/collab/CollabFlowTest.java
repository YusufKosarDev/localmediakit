package com.localmediakit.collab;

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
class CollabFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Collab Owner"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createKit(String token, String title) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private long addCollab(String token, long kitId, String brand, int order) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"%s\",\"displayOrder\":%d}".formatted(brand, order)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    void crudRoundTrip() throws Exception {
        String token = register("collab-crud@example.com");
        long kitId = createKit(token, "Collab Kit");

        long id = addCollab(token, kitId, "Marka A", 0);

        mockMvc.perform(put("/api/mediakits/" + kitId + "/collaborations/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brandName":"Marka A","campaign":"Yaz kampanyasi",
                                 "period":"2026 Q2","resultNote":"500K izlenme","displayOrder":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaign").value("Yaz kampanyasi"));

        mockMvc.perform(get("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].resultNote").value("500K izlenme"));

        mockMvc.perform(delete("/api/mediakits/" + kitId + "/collaborations/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listFollowsDisplayOrderNotInsertionOrder() throws Exception {
        String token = register("collab-order@example.com");
        long kitId = createKit(token, "Sirali Kit");

        addCollab(token, kitId, "Sonuncu", 2);
        addCollab(token, kitId, "Birinci", 0);
        long middle = addCollab(token, kitId, "Ortada", 1);

        mockMvc.perform(get("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].brandName").value("Birinci"))
                .andExpect(jsonPath("$[1].brandName").value("Ortada"))
                .andExpect(jsonPath("$[2].brandName").value("Sonuncu"));

        // Reorder: moving the middle one to the front changes the listing.
        mockMvc.perform(put("/api/mediakits/" + kitId + "/collaborations/" + middle)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"Ortada\",\"displayOrder\":-0}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].brandName").value("Birinci"))
                .andExpect(jsonPath("$[1].brandName").value("Ortada"));
    }

    @Test
    void collaborationsAreOwnerScoped() throws Exception {
        String owner = register("collab-owner@example.com");
        String stranger = register("collab-stranger@example.com");
        long kitId = createKit(owner, "Korunan Collab");
        long collabId = addCollab(owner, kitId, "Gizli Marka", 0);

        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"X\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"X\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/mediakits/" + kitId + "/collaborations/" + collabId)
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"Hacked\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/mediakits/" + kitId + "/collaborations/" + collabId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void collabIdsAreScopedToTheirKit() throws Exception {
        String token = register("collab-scope@example.com");
        long kitA = createKit(token, "Kit A");
        long collabId = addCollab(token, kitA, "Marka", 0);

        // Same owner, but the collab id addressed through a WRONG kit id -> 404.
        String other = register("collab-scope-b@example.com");
        long kitB = createKit(other, "Kit B");
        mockMvc.perform(delete("/api/mediakits/" + kitB + "/collaborations/" + collabId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void brandNameIsRequired() throws Exception {
        String token = register("collab-valid@example.com");
        long kitId = createKit(token, "Valid Kit");
        mockMvc.perform(post("/api/mediakits/" + kitId + "/collaborations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
