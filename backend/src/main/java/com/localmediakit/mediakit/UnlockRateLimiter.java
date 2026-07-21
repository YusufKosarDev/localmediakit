package com.localmediakit.mediakit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Brute-force guard for password unlock attempts. Sliding window of FAILED
 * attempts per key (slug + client ip): a legitimate visitor succeeds on the
 * first try and is never limited, while repeated wrong guesses are throttled.
 *
 * Backed by a bounded, self-evicting Caffeine cache (idle keys expire after the
 * window, total capped) so a flood of distinct keys cannot exhaust memory.
 * In-memory by design; a restart simply resets the counters.
 */
@Component
public class UnlockRateLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Cache<String, Deque<Instant>> failures = Caffeine.newBuilder()
            .expireAfterAccess(WINDOW)
            .maximumSize(50_000)
            .build();

    /** Throws if the key has already used up its failed-attempt budget. */
    public void checkAllowed(String key) {
        Deque<Instant> attempts = failures.getIfPresent(key);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= MAX_FAILURES) {
                throw new TooManyUnlockAttemptsException();
            }
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> attempts = failures.get(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(Instant.now());
        }
    }

    /** A correct password clears the budget so the visitor is not penalised. */
    public void reset(String key) {
        failures.invalidate(key);
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }
}
