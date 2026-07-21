package com.localmediakit.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Holds one token bucket per (rule, client) key. Backed by a bounded,
 * self-evicting Caffeine cache: idle keys expire and the total is capped, so a
 * flood of distinct keys can never exhaust memory. In-memory by design (single
 * Render instance); swap for a Redis-backed Bucket4j to scale out.
 */
@Component
public class RateLimiterRegistry {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    /**
     * @return true if a token was available (request allowed), false if the
     *         bucket for this key is exhausted (should be rejected).
     */
    public boolean tryConsume(String key, long capacityPerMinute) {
        Bucket bucket = buckets.get(key, k -> newBucket(capacityPerMinute));
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(long capacityPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacityPerMinute)
                .refillGreedy(capacityPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
