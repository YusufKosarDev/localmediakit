package com.localmediakit.mediakit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Brute-force guard for password unlock attempts. Sliding window of FAILED
 * attempts per key (slug + client ip): a legitimate visitor succeeds on the
 * first try and is never counted, while repeated wrong guesses are throttled.
 *
 * <p><b>In the database, not in memory.</b> This was a Caffeine cache, which is
 * the right shape for the problem and the wrong scope for the deployment. A
 * per-JVM counter answers "has this client failed five times against this
 * instance", and the moment there are two instances that stops being the
 * question anyone meant to ask: the budget becomes five per instance, an
 * attacker gets 5 x N attempts without doing anything clever, and nothing
 * anywhere reports that the limit moved. A guard that degrades silently under
 * the exact condition it was written for is worse than no guard, because it is
 * still believed.
 *
 * <p>The cost is a query on a path that is already slow — an unlock does a
 * BCrypt comparison — and the volume is bounded by the guard itself: five rows
 * per key, deleted on success.
 *
 * <p>Every method runs in its own transaction. {@code recordFailure} is called
 * from a read-only transaction and immediately before an exception is thrown to
 * reject the attempt; joining that transaction would make the write either
 * illegal or rolled back, and a brute-force counter that forgets the attempt it
 * just refused counts nothing.
 */
@Component
public class UnlockRateLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final KitUnlockFailureRepository failures;

    public UnlockRateLimiter(KitUnlockFailureRepository failures) {
        this.failures = failures;
    }

    /** Throws if the key has already used up its failed-attempt budget. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void checkAllowed(String key) {
        if (failures.countByAttemptKeyAndFailedAtAfter(key, windowStart()) >= MAX_FAILURES) {
            throw new TooManyUnlockAttemptsException();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String key) {
        Instant now = Instant.now();
        failures.save(new KitUnlockFailure(key, now));
        // Swept here rather than from a scheduled job: this path runs only on a
        // wrong password, which is rare and already slow, and it keeps the
        // table bounded without another job, another lock name and another
        // thing to explain.
        failures.deleteExpired(now.minus(WINDOW));
    }

    /** A correct password clears the budget so the visitor is not penalised. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reset(String key) {
        failures.deleteByAttemptKey(key);
    }

    private static Instant windowStart() {
        return Instant.now().minus(WINDOW);
    }
}
