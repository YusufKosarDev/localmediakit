package com.localmediakit.mediakit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Listing kits must not cost a query per kit.
 *
 * <p>{@code MediaKitService.list} resolves every kit's published slug in one
 * query on purpose -- the obvious version asks the version table once per kit.
 * That care was invisible: nothing failed if someone replaced the batched
 * lookup with a loop, because the endpoint returns identical JSON either way.
 * It would just get slower in proportion to how much someone uses the product,
 * on a free database whose connection budget is five.
 *
 * <p>The assertion is a ceiling rather than an exact count. Pinning the precise
 * number would break on any harmless change -- an added column, a different
 * fetch strategy -- and a test that cries wolf gets its number bumped until it
 * means nothing. A ceiling that does not grow with the number of kits is the
 * property actually worth keeping, so it is checked by comparing two accounts
 * of very different sizes.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class KitListQueryCountTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void listingTenKitsCostsNoMoreQueriesThanListingOne() throws Exception {
        long forOneKit = queriesToListKitsOf("n1-small@example.com", 1);
        long forTenKits = queriesToListKitsOf("n1-large@example.com", 10);

        assertThat(forTenKits)
                .as("query count must not grow with the number of kits (was %d for 1, %d for 10)",
                        forOneKit, forTenKits)
                .isLessThanOrEqualTo(forOneKit);
    }

    @Test
    void theListEndpointStaysWithinASmallFixedBudget() throws Exception {
        // A second, blunter guard. The comparison above would still pass if
        // both sides regressed together -- a per-kit query added to the shared
        // path, say -- and a flat number catches that.
        assertThat(queriesToListKitsOf("n1-budget@example.com", 5)).isLessThanOrEqualTo(6);
    }

    /** Publishes each kit, so the published-slug lookup is actually exercised. */
    private long queriesToListKitsOf(String email, int kitCount) throws Exception {
        String token = register(email);
        for (int i = 1; i <= kitCount; i++) {
            long kitId = createKit(token, "N Plus One " + i);
            mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/mediakits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(kitCount));

        return statistics.getPrepareStatementCount();
    }

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Sorgu Sayaci"}
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
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }
}
