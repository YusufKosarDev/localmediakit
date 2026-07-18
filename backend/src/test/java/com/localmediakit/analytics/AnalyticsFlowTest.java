package com.localmediakit.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.user.Plan;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsFlowTest {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0";
    private static final String MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148 Safari/604.1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PageViewRepository pageViewRepository;

    @Autowired
    private UserRepository userRepository;

    private String register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"supersecret","displayName":"Analytics Owner"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createPublishedKit(String token, String title) throws Exception {
        String created = mockMvc.perform(post("/api/mediakits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long kitId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/mediakits/" + kitId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return kitId;
    }

    private MockHttpServletRequestBuilder beacon(String slug, String ip, String userAgent) {
        return post("/api/track")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"%s\"}".formatted(slug))
                .header("User-Agent", userAgent)
                .header("X-Forwarded-For", ip);
    }

    @Test
    void beaconCountsAViewForThePublishedSlug() throws Exception {
        String token = register("track-basic@example.com");
        long kitId = createPublishedKit(token, "Takipli Kit");

        mockMvc.perform(beacon("takipli-kit", "203.0.113.10", BROWSER_UA))
                .andExpect(status().isAccepted());

        assertEquals(1, pageViewRepository.countByMediaKitId(kitId));
    }

    @Test
    void unknownSlugIsAcceptedButNotCounted() throws Exception {
        long before = pageViewRepository.count();
        mockMvc.perform(beacon("boyle-bir-sayfa-yok", "203.0.113.11", BROWSER_UA))
                .andExpect(status().isAccepted());
        assertEquals(before, pageViewRepository.count());
    }

    @Test
    void sameVisitorWithinSessionWindowCountsOnce() throws Exception {
        String token = register("track-dedup@example.com");
        long kitId = createPublishedKit(token, "Dedup Kit");

        mockMvc.perform(beacon("dedup-kit", "203.0.113.20", BROWSER_UA))
                .andExpect(status().isAccepted());
        mockMvc.perform(beacon("dedup-kit", "203.0.113.20", BROWSER_UA))
                .andExpect(status().isAccepted());
        mockMvc.perform(beacon("dedup-kit", "203.0.113.20", BROWSER_UA))
                .andExpect(status().isAccepted());

        assertEquals(1, pageViewRepository.countByMediaKitId(kitId));

        // A different visitor (other IP) is a separate count.
        mockMvc.perform(beacon("dedup-kit", "203.0.113.21", BROWSER_UA))
                .andExpect(status().isAccepted());
        assertEquals(2, pageViewRepository.countByMediaKitId(kitId));
    }

    @Test
    void botsAndHeadlessClientsAreNotCounted() throws Exception {
        String token = register("track-bot@example.com");
        long kitId = createPublishedKit(token, "Bot Kit");

        mockMvc.perform(beacon("bot-kit", "203.0.113.30", "Googlebot/2.1 (+http://www.google.com/bot.html)"))
                .andExpect(status().isAccepted());
        mockMvc.perform(beacon("bot-kit", "203.0.113.31", "curl/8.4.0"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"bot-kit\"}")
                        .header("X-Forwarded-For", "203.0.113.32")) // no User-Agent at all
                .andExpect(status().isAccepted());

        assertEquals(0, pageViewRepository.countByMediaKitId(kitId));
    }

    @Test
    void freePlanSeesOnlyTheTotalCounter() throws Exception {
        String token = register("track-free@example.com");
        long kitId = createPublishedKit(token, "Free Analitik");
        mockMvc.perform(beacon("free-analitik", "203.0.113.40", BROWSER_UA))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/mediakits/" + kitId + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.totalViews").value(1))
                .andExpect(jsonPath("$.uniqueVisitors").doesNotExist())
                .andExpect(jsonPath("$.viewsByDay").doesNotExist())
                .andExpect(jsonPath("$.referrers").doesNotExist());
    }

    @Test
    void proPlanGetsTheFullBreakdown() throws Exception {
        String token = register("track-pro@example.com");
        long kitId = createPublishedKit(token, "Pro Analitik");
        User owner = userRepository.findByEmail("track-pro@example.com").orElseThrow();
        owner.changePlan(Plan.PRO);
        userRepository.save(owner);

        // Two live beacons: desktop with a referrer-less hit, mobile from Instagram.
        mockMvc.perform(beacon("pro-analitik", "203.0.113.50", BROWSER_UA))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"pro-analitik\",\"referrer\":\"https://www.instagram.com/stories/x\"}")
                        .header("User-Agent", MOBILE_UA)
                        .header("X-Forwarded-For", "203.0.113.51"))
                .andExpect(status().isAccepted());
        // Plus a backdated view 5 days ago from a returning visitor hash.
        pageViewRepository.save(new PageView(kitId, "pro-analitik", "eskiziyaretci",
                "instagram.com", "MOBILE", Instant.now().minus(5, ChronoUnit.DAYS)));

        mockMvc.perform(get("/api/mediakits/" + kitId + "/analytics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.totalViews").value(3))
                .andExpect(jsonPath("$.uniqueVisitors").value(3))
                .andExpect(jsonPath("$.viewsByDay.length()").value(2))
                .andExpect(jsonPath("$.viewsByDay[1].views").value(2))
                .andExpect(jsonPath("$.referrers[?(@.label=='www.instagram.com')].count").value(1))
                .andExpect(jsonPath("$.referrers[?(@.label=='instagram.com')].count").value(1))
                .andExpect(jsonPath("$.devices[?(@.label=='MOBILE')].count").value(2))
                .andExpect(jsonPath("$.devices[?(@.label=='DESKTOP')].count").value(1));
    }

    @Test
    void analyticsAreOwnerScoped() throws Exception {
        String owner = register("track-owner@example.com");
        String stranger = register("track-stranger@example.com");
        long kitId = createPublishedKit(owner, "Korunan Analitik");

        mockMvc.perform(get("/api/mediakits/" + kitId + "/analytics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mediakits/" + kitId + "/analytics")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }
}
