package com.localmediakit.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property the move to the database was for.
 *
 * <p>A second application instance is a second {@code RateLimiterRegistry}
 * reading the same table, so that is what these tests build. When the buckets
 * were a Caffeine cache the two would have had a budget each and every
 * assertion below would have measured double — which is exactly the bug: the
 * configured limit says ten and the deployment allows twenty, with nothing
 * logged and nothing to notice.
 */
@SpringBootTest
class SharedRateLimitTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** A separate instance of the registry, on the same database. */
    private RateLimiterRegistry anotherInstance() {
        return new RateLimiterRegistry(jdbcTemplate);
    }

    @Test
    void twoInstancesShareOneBudget() {
        RateLimiterRegistry first = anotherInstance();
        RateLimiterRegistry second = anotherInstance();
        String key = "shared|198.51.100.7";

        // Five tokens, spent alternately by the two instances.
        for (int i = 0; i < 5; i++) {
            RateLimiterRegistry instance = i % 2 == 0 ? first : second;
            assertThat(instance.tryConsume(key, 5))
                    .as("token %d should be available", i + 1)
                    .isTrue();
        }

        // The budget is gone, and it is gone on both.
        assertThat(first.tryConsume(key, 5)).as("first instance is out").isFalse();
        assertThat(second.tryConsume(key, 5)).as("second instance is out too").isFalse();
    }

    @Test
    void aBucketRefillsOverTime() {
        RateLimiterRegistry registry = anotherInstance();
        String key = "refill|198.51.100.8";

        assertThat(registry.tryConsume(key, 2)).isTrue();
        assertThat(registry.tryConsume(key, 2)).isTrue();
        assertThat(registry.tryConsume(key, 2)).as("budget spent").isFalse();

        // Rewind the clock on the row rather than sleeping through a refill: at
        // two per minute, waiting for a token would put half a minute into the
        // suite for something the arithmetic already decides.
        jdbcTemplate.update(
                "UPDATE rate_limit_buckets SET last_refill_ms = last_refill_ms - ? WHERE bucket_key = ?",
                Duration.ofMinutes(1).toMillis(), key);

        assertThat(registry.tryConsume(key, 2)).as("a minute later, refilled").isTrue();
    }

    /**
     * Two instances hitting an unseen key at the same moment both try to create
     * the bucket, and one loses the primary key. The loser must not be handed
     * a fresh full bucket, and must not fail either.
     */
    @Test
    void aRaceToCreateTheSameBucketStillSpendsOneBudget() throws Exception {
        String key = "race|198.51.100.9";
        int racers = 8;
        int capacity = 4;

        CyclicBarrier startTogether = new CyclicBarrier(racers);
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            Future<?>[] running = new Future<?>[racers];
            for (int i = 0; i < racers; i++) {
                RateLimiterRegistry instance = anotherInstance();
                running[i] = pool.submit(() -> {
                    startTogether.await(5, TimeUnit.SECONDS);
                    if (instance.tryConsume(key, capacity)) {
                        allowed.incrementAndGet();
                    }
                    return null;
                });
            }
            for (Future<?> f : running) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed.get())
                .as("the bucket is one budget, however many instances raced for it")
                .isEqualTo(capacity);
    }

    @Test
    void staleBucketsAreSweptAway() {
        RateLimiterRegistry registry = anotherInstance();
        String key = "stale|198.51.100.10";
        registry.tryConsume(key, 5);

        jdbcTemplate.update(
                "UPDATE rate_limit_buckets SET last_refill_ms = ? WHERE bucket_key = ?",
                System.currentTimeMillis() - Duration.ofHours(2).toMillis(), key);

        registry.sweepStale(System.currentTimeMillis() - RateLimiterRegistry.STALE_AFTER.toMillis());

        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rate_limit_buckets WHERE bucket_key = ?", Long.class, key);
        assertThat(remaining).as("an untouched bucket is storage, not state").isZero();
    }
}
