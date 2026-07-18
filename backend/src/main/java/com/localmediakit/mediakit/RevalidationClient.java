package com.localmediakit.mediakit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Triggers on-demand revalidation of a public page on the frontend (Next.js).
 * This is the heart of the architecture: publishing on the backend pushes an
 * invalidation to the edge, so public pages stay static/edge-cached and never
 * depend on this backend being awake.
 */
@Component
public class RevalidationClient {

    private static final Logger log = LoggerFactory.getLogger(RevalidationClient.class);

    // Force HTTP/1.1: the default HTTP/2 upgrade negotiation is unreliable against
    // the Next.js (Node) server and fails with "header parser received no bytes".
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String revalidateUrl;
    private final String revalidateSecret;

    public RevalidationClient(
            @Value("${app.revalidate.url}") String revalidateUrl,
            @Value("${app.revalidate.secret}") String revalidateSecret) {
        this.revalidateUrl = revalidateUrl;
        this.revalidateSecret = revalidateSecret;
    }

    /**
     * @return the HTTP status returned by the frontend, or -1 if it could not be reached.
     */
    public int revalidate(String slug) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("slug", slug));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(revalidateUrl))
                    .header("Content-Type", "application/json")
                    .header("x-revalidate-secret", revalidateSecret)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Revalidation for slug '{}' returned status {}", slug, response.statusCode());
            return response.statusCode();
        } catch (Exception e) {
            log.warn("Revalidation for slug '{}' failed: {}", slug, e.getMessage());
            return -1;
        }
    }
}
