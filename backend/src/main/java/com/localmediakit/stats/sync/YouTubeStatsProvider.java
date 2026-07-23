package com.localmediakit.stats.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.localmediakit.stats.Platform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.function.Function;

/**
 * YouTube Data API v3, {@code channels.list} with an API key — public channel
 * statistics only, no OAuth. Graceful-enable: without YOUTUBE_API_KEY the
 * provider reports unavailable and the whole feature stays dark.
 *
 * Accepted external ids: a raw channel id ({@code UC...}) or a handle
 * ({@code @name} / {@code name}).
 */
@Component
public class YouTubeStatsProvider implements StatsProvider {

    /** Channel ids are "UC" + 22 URL-safe base64 chars. */
    private static final String CHANNEL_ID_PATTERN = "^UC[\\w-]{22}$";

    private final RestClient restClient;
    private final String apiKey;

    public YouTubeStatsProvider(
            @Value("${app.youtube.api-key:}") String apiKey,
            @Value("${app.youtube.api-base-url:https://www.googleapis.com/youtube/v3}") String baseUrl,
            @Value("${app.statsync.http-timeout-ms:5000}") int timeoutMs) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public Platform platform() {
        return Platform.YOUTUBE;
    }

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public FetchedStats fetch(String externalId) {
        String id = externalId.trim();
        Function<UriBuilder, URI> uri = builder -> {
            builder.path("/channels").queryParam("part", "statistics").queryParam("key", apiKey);
            return (id.matches(CHANNEL_ID_PATTERN)
                    ? builder.queryParam("id", id)
                    : builder.queryParam("forHandle", id.startsWith("@") ? id : "@" + id))
                    .build();
        };

        JsonNode body;
        try {
            body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            // 403 on this endpoint is the quota signal (the key itself was accepted).
            if (e.getStatusCode().value() == 403) {
                throw new StatsProviderException(StatsProviderException.Kind.QUOTA,
                        "YouTube API quota exceeded");
            }
            throw new StatsProviderException(StatsProviderException.Kind.TRANSIENT,
                    "YouTube API rejected the request (" + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            throw new StatsProviderException(StatsProviderException.Kind.TRANSIENT,
                    "YouTube API unreachable");
        }

        JsonNode items = body == null ? null : body.path("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            throw new StatsProviderException(StatsProviderException.Kind.NOT_FOUND,
                    "YouTube channel not found: " + id);
        }
        JsonNode stats = items.get(0).path("statistics");
        if (stats.path("hiddenSubscriberCount").asBoolean(false)) {
            throw new StatsProviderException(StatsProviderException.Kind.NOT_FOUND,
                    "Channel hides its subscriber count");
        }
        long subscribers = stats.path("subscriberCount").asLong(-1);
        if (subscribers < 0) {
            throw new StatsProviderException(StatsProviderException.Kind.TRANSIENT,
                    "Unexpected YouTube API payload");
        }
        // Crude but honest average: lifetime views per uploaded video. Good
        // enough for a trend line; a per-video window is a later refinement.
        long viewCount = stats.path("viewCount").asLong(0);
        long videoCount = stats.path("videoCount").asLong(0);
        Long avgViews = videoCount > 0 ? viewCount / videoCount : null;

        return new FetchedStats(subscribers, avgViews, null, null);
    }
}
