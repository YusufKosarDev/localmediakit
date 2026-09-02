package com.localmediakit.ratelimit;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * One token bucket per (rule, client) key, held in the database.
 *
 * <p><b>Why not in memory.</b> This was a Caffeine cache of Bucket4j buckets,
 * which is faster and, on more than one instance, wrong in a way that does not
 * announce itself. Each JVM would keep its own bucket for the same IP, so a
 * limit of ten logins a minute becomes ten per instance; the configuration
 * still says ten, the logs still say nothing, and the only way to notice is to
 * go looking. The failure mode of the thing that exists to stop brute force
 * should not be "silently allows N times more brute force".
 *
 * <p><b>The cost, stated plainly.</b> Every throttled endpoint now does one
 * database round-trip before it does anything else. That is a real price on
 * the view beacon, which is the busiest public path — though that request was
 * already going to write a page-view row, so it was never free. The bucket is
 * one small row and the hot path is a single UPDATE.
 *
 * <p><b>Why one UPDATE.</b> Refilling and spending are a read-modify-write, and
 * doing them as SELECT-then-UPDATE would let two requests read the same token
 * count and both spend it. Computing the refill inside the UPDATE's SET and
 * WHERE means the database's row lock serialises them: the statement either
 * finds a token and takes it, or matches no row and the caller is throttled.
 */
@Component
public class RateLimiterRegistry implements TokenBuckets {

    /**
     * A bucket untouched for this long is discarded. It refills to full in a
     * minute, so an old row and a missing row mean the same thing; keeping it
     * would only grow the table by every IP ever seen.
     */
    static final Duration STALE_AFTER = Duration.ofMinutes(30);

    /**
     * Spend a token if the bucket has one, having first refilled it for the
     * time that passed. The refill is capped at capacity, which is what makes
     * it a bucket rather than a running total.
     *
     * <p>The shape of the refill term is not cosmetic. Written the obvious way
     * -- elapsed multiplied by a fractional tokens-per-millisecond parameter --
     * H2 infers the parameter's type from the BIGINT it is multiplying and
     * rounds a rate like 0.0000333 to zero, so buckets never refill. Postgres
     * does not, which is the bad half: the throttle would have been broken
     * locally and in every test, and correct in production, for as long as it
     * took someone to notice their bucket never came back.
     *
     * <p>So both parameters here are whole numbers, and the only fractional
     * value is the literal below -- milliseconds in a minute -- which each
     * database parses as a decimal on its own. Integer maths first, one
     * division last.
     */
    private static final String CONSUME = """
            UPDATE rate_limit_buckets
               SET tokens = LEAST(?, tokens + (? - last_refill_ms) * ? / 60000.0) - 1,
                   last_refill_ms = ?
             WHERE bucket_key = ?
               AND LEAST(?, tokens + (? - last_refill_ms) * ? / 60000.0) >= 1
            """;

    private static final String EXISTS =
            "SELECT COUNT(*) FROM rate_limit_buckets WHERE bucket_key = ?";

    private static final String CREATE =
            "INSERT INTO rate_limit_buckets (bucket_key, tokens, last_refill_ms) VALUES (?, ?, ?)";

    private static final String SWEEP =
            "DELETE FROM rate_limit_buckets WHERE last_refill_ms < ?";

    private final JdbcTemplate jdbc;

    public RateLimiterRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryConsume(String key, long capacityPerMinute) {
        if (capacityPerMinute <= 0) {
            // A capacity of zero is a closed door, not an empty bucket that
            // refills. Reading it as the latter would let the first request
            // through on a rule configured to allow none.
            return false;
        }
        long now = System.currentTimeMillis();

        if (spend(key, capacityPerMinute, now)) {
            return true;
        }
        // No row was updated, which means either the bucket is empty or this
        // key has never been seen. Only the second case is worth a write, and
        // asking costs nothing next to creating a row on every throttled
        // request and rolling it back.
        if (exists(key)) {
            // The row is there now. That is not the same as "there is no token
            // left": it may have appeared between the attempt above and this
            // question, put there by another request for the same new key.
            // Answering false here would refuse a request against a bucket
            // that had just been filled -- which is what a burst of traffic
            // from one new client looks like, so the first few requests from
            // every new IP were being throttled for no reason.
            //
            // Whether a token is available is spend()'s question, and it is
            // asked again rather than inferred. The cost is one more statement
            // on a request that is about to be rejected anyway.
            return spend(key, capacityPerMinute, now);
        }
        return createAndSpend(key, capacityPerMinute, now);
    }

    private boolean spend(String key, long capacityPerMinute, long now) {
        // The cap is bound as a double so LEAST compares like with like; the
        // multiplier is bound as a whole number so nothing can round it away.
        double cap = capacityPerMinute;
        return jdbc.update(CONSUME,
                cap, now, capacityPerMinute, now,
                key,
                cap, now, capacityPerMinute) == 1;
    }

    private boolean exists(String key) {
        Long count = jdbc.queryForObject(EXISTS, Long.class, key);
        return count != null && count > 0;
    }

    /**
     * First request for this key: the bucket starts full and this request
     * spends one token from it.
     *
     * <p>Two requests can reach here for the same new key at once, and exactly
     * one will win the primary key. The loser is not an error — the bucket it
     * wanted now exists — so it retries the ordinary path and is answered by
     * whatever the winner left behind.
     */
    private boolean createAndSpend(String key, long capacityPerMinute, long now) {
        try {
            jdbc.update(CREATE, key, (double) capacityPerMinute - 1, now);
            // Cheap, and only on a key's first request rather than on every
            // one. That is often enough to keep the table to the clients
            // actually being seen.
            jdbc.update(SWEEP, now - STALE_AFTER.toMillis());
            return true;
        } catch (DataIntegrityViolationException lostTheRace) {
            return spend(key, capacityPerMinute, now);
        }
    }

    /**
     * Exposed for the sweep's own test. ConstraintRetry, which the write
     * paths elsewhere use, is deliberately not applied here: the collision
     * this method can hit is resolved by reading what the winner wrote, not by
     * attempting the same write again.
     */
    int sweepStale(long olderThanMillis) {
        return jdbc.update(SWEEP, olderThanMillis);
    }
}
