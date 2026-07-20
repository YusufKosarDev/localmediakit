package com.localmediakit.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Holds one token bucket per (rule, client) key. In-memory: a single Render
 * instance makes a distributed store unnecessary; the interface is small enough
 * to swap for a Redis-backed Bucket4j later if the deployment ever scales out.
 */
@Component
public class RateLimiterRegistry {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * @return true if a token was available (request allowed), false if the
     *         bucket for this key is exhausted (should be rejected).
     */
    public boolean tryConsume(String key, long capacityPerMinute) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(capacityPerMinute));
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
