package com.localmediakit.ratelimit;

/**
 * "May this key spend a token right now?"
 *
 * <p>The one thing {@link RateLimitFilter} needs to know, and the only thing it
 * should know. Where the buckets live is the other side's business: it was a
 * cache in this JVM, it is now a row in the database, and the filter's job --
 * deciding which rule a request falls under -- did not change either time.
 *
 * <p>That separation is also what keeps the routing test honest. Which of the
 * six rules matches a path is a pure function of method and URI, and it is the
 * part most likely to break silently, so its test should not need a database
 * to answer it.
 */
public interface TokenBuckets {

    /**
     * @return true if a token was available (request allowed), false if the
     *         bucket for this key is exhausted (should be rejected).
     */
    boolean tryConsume(String key, long capacityPerMinute);
}
