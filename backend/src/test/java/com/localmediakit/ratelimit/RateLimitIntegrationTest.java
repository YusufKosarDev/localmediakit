package com.localmediakit.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the real servlet filter chain (RANDOM_PORT) with rate limiting
 * enabled and a tiny capacity, proving the filter is wired ahead of the app and
 * returns 429 once the bucket is drained.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.ratelimit.enabled=true",
                "app.ratelimit.track-capacity=3",
                "app.demo.seed-on-startup=false"
        })
class RateLimitIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void trackBeaconIsRateLimitedPerClient() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> body = new HttpEntity<>("{\"slug\":\"whatever\"}", headers);

        // Capacity is 3: the first three are accepted...
        for (int i = 1; i <= 3; i++) {
            ResponseEntity<String> res = rest.postForEntity("/api/track", body, String.class);
            assertEquals(HttpStatus.ACCEPTED, res.getStatusCode(), "request " + i + " should pass");
        }
        // ...the fourth from the same client is throttled.
        ResponseEntity<String> fourth = rest.postForEntity("/api/track", body, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, fourth.getStatusCode());
    }
}
