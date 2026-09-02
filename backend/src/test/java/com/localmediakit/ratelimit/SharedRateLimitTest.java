package com.localmediakit.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Duration;
import java.util.UUID;
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
class SharedRateLimitTest {

    private JdbcTemplate jdbcTemplate;

    /**
     * Its own database, and no Spring context.
     *
     * <p>The registry needs a JdbcTemplate and nothing else, so booting an
     * application to hand it one buys nothing and costs two things that matter
     * here. It is slow, and this class is on the mutation-coverage list, where
     * every mutant re-runs it. And it would share the suite-wide in-memory
     * database, so these buckets would sit alongside every other test's and the
     * fixed keys below — chosen so each assertion reads as the scenario it
     * describes — would stop being reliable.
     *
     * <p>The schema comes from the migration itself rather than a copy of it,
     * so a column that changes there cannot leave this test passing against a
     * table the application no longer has.
     */
    @BeforeEach
    void freshDatabase() {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(
                new org.h2.Driver(),
                // DB_CLOSE_DELAY=-1 or the database is dropped the moment the
                // populator's connection closes and every query below finds an
                // empty schema. The name is unique per test, so keeping it
                // alive costs nothing and leaks into nothing.
                "jdbc:h2:mem:ratelimit-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        // A populator rather than execute(): the migration is a table and two
        // indexes, and execute() runs one statement.
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V31__create_rate_limit_buckets.sql"));
        schema.execute(dataSource);
    }

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

        int swept = registry.sweepStale(
                System.currentTimeMillis() - RateLimiterRegistry.STALE_AFTER.toMillis());

        // The count is asserted, not just the effect. It is what the caller
        // would log or act on, and a sweep that deletes rows while reporting
        // nothing is a sweep nobody can tell ran.
        assertThat(swept).as("one stale bucket was swept").isEqualTo(1);

        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rate_limit_buckets WHERE bucket_key = ?", Long.class, key);
        assertThat(remaining).as("an untouched bucket is storage, not state").isZero();
    }

    /**
     * A rule configured to allow nothing must allow nothing.
     *
     * <p>Zero is a closed door, not an empty bucket that refills a moment
     * later. Read the wrong way it lets the first request of every minute
     * through, which is the one request that matters on a rule somebody set to
     * zero deliberately.
     */
    @Test
    void aCapacityOfZeroRefusesEveryRequest() {
        RateLimiterRegistry registry = anotherInstance();

        assertThat(registry.tryConsume("closed|198.51.100.11", 0)).isFalse();
        assertThat(registry.tryConsume("closed|198.51.100.11", 0)).isFalse();

        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rate_limit_buckets", Long.class);
        assertThat(rows).as("a refused rule does not need a bucket").isZero();
    }
}
