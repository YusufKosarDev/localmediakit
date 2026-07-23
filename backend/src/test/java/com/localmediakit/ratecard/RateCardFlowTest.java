package com.localmediakit.ratecard;

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
class RateCardFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Fiyatci"}
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

    private long addItem(String token, long kitId, String json) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits/" + kitId + "/ratecard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    void crudRoundTripInDisplayOrder() throws Exception {
        String token = register("rate-crud@example.com");
        long kitId = createKit(token, "{\"title\":\"Fiyatli Kit\"}");

        long second = addItem(token, kitId,
                "{\"serviceName\":\"Instagram Reels\",\"priceAmount\":9000,\"displayOrder\":1}");
        addItem(token, kitId,
                "{\"serviceName\":\"YouTube video\",\"priceAmount\":25000,\"currency\":\"TRY\",\"note\":\"60 sn entegrasyon\",\"displayOrder\":0}");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/ratecard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].serviceName").value("YouTube video"))
                .andExpect(jsonPath("$[0].currency").value("TRY"))
                .andExpect(jsonPath("$[1].serviceName").value("Instagram Reels"));

        mockMvc.perform(put("/api/mediakits/" + kitId + "/ratecard/" + second)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"Instagram Reels\",\"priceAmount\":11000,\"currency\":\"USD\",\"displayOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAmount").value(11000))
                .andExpect(jsonPath("$.currency").value("USD"));

        mockMvc.perform(delete("/api/mediakits/" + kitId + "/ratecard/" + second)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/ratecard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void validationRejectsBadCurrencyAndNegativePrice() throws Exception {
        String token = register("rate-valid@example.com");
        long kitId = createKit(token, "{\"title\":\"Dogrulama\"}");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/ratecard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"X\",\"priceAmount\":100,\"currency\":\"BTC\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/mediakits/" + kitId + "/ratecard")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"X\",\"priceAmount\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rateCardIsOwnerScoped() throws Exception {
        String owner = register("rate-owner@example.com");
        String stranger = register("rate-stranger@example.com");
        long kitId = createKit(owner, "{\"title\":\"Sahipli\"}");
        long itemId = addItem(owner, kitId, "{\"serviceName\":\"Video\",\"priceAmount\":100}");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/ratecard")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/mediakits/" + kitId + "/ratecard/" + itemId)
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"Hack\",\"priceAmount\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rateCardIsFrozenIntoTheSnapshotAtPublishTime() throws Exception {
        String token = register("rate-freeze@example.com");
        long kitId = createKit(token, "{\"title\":\"Donmus Fiyat\"}");
        addItem(token, kitId, "{\"serviceName\":\"YouTube video\",\"priceAmount\":25000,\"displayOrder\":0}");

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/kits/donmus-fiyat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateCard.length()").value(1))
                .andExpect(jsonPath("$.rateCard[0].serviceName").value("YouTube video"))
                .andExpect(jsonPath("$.rateCard[0].priceAmount").value(25000))
                .andExpect(jsonPath("$.rateCard[0].currency").value("TRY"));

        // An item added AFTER publish must not appear until re-publish.
        addItem(token, kitId, "{\"serviceName\":\"Sizinti\",\"priceAmount\":1,\"displayOrder\":1}");
        mockMvc.perform(get("/api/public/kits/donmus-fiyat"))
                .andExpect(jsonPath("$.rateCard.length()").value(1));

        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/kits/donmus-fiyat"))
                .andExpect(jsonPath("$.rateCard.length()").value(2));
    }
}
