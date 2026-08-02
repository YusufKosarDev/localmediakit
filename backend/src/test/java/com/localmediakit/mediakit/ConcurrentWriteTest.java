package com.localmediakit.mediakit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The write paths that choose a value by reading what is already there.
 *
 * <p>Each of these was a check-then-write with nothing between the two steps,
 * so a second request arriving in the gap lost to a unique constraint and the
 * violation escaped as a 500. None of it needed unusual load to reach: two
 * people naming a kit the same thing, or one person double-clicking Publish.
 *
 * <p>A barrier is used rather than hope. Simply starting two threads usually
 * runs them one after the other and the test passes without ever creating the
 * condition it claims to test; releasing both from the same barrier makes the
 * overlap the normal case rather than the lucky one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentWriteTest {

    private static final int CONTENDERS = 4;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void racingCreatesWithTheSameTitleAllSucceedWithDistinctSlugs() throws Exception {
        String token = register("race-slug@example.com");

        List<MvcResult> results = inParallel(() -> mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ayni Baslik\"}"))
                .andReturn());

        List<String> slugs = new ArrayList<>();
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus())
                    .as("a losing contender used to get a 500 here")
                    .isEqualTo(201);
            slugs.add(objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("slug").asText());
        }
        // The suffix logic already existed; the race was stopping it running.
        assertThat(slugs).doesNotHaveDuplicates().hasSize(CONTENDERS);
    }

    @Test
    void racingPublishesOfOneKitProduceConsecutiveVersions() throws Exception {
        String token = register("race-publish@example.com");
        long kitId = createKit(token, "Yarisan Yayin");

        List<MvcResult> results = inParallel(() -> mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                .header("Authorization", "Bearer " + token)).andReturn());

        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus())
                    .as("a double-clicked publish used to produce a 500")
                    .isEqualTo(200);
        }

        String versions = mockMvc.perform(get("/api/mediakits/" + kitId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> numbers = new ArrayList<>();
        objectMapper.readTree(versions).forEach(node -> numbers.add(node.get("version").asInt()));

        assertThat(numbers).doesNotHaveDuplicates().hasSize(CONTENDERS);
        assertThat(numbers).containsExactlyInAnyOrder(1, 2, 3, 4);
    }

    /**
     * Releases every call from one barrier so they overlap, and rethrows the
     * first failure rather than letting an exception vanish into a Future.
     */
    private List<MvcResult> inParallel(Callable<MvcResult> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        CyclicBarrier startTogether = new CyclicBarrier(CONTENDERS);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < CONTENDERS; i++) {
                futures.add(pool.submit(() -> {
                    startTogether.await();
                    return call.call();
                }));
            }
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Yarisci"}
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
