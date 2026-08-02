package com.localmediakit.shared;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The retry rule on its own, without a database. The integration tests prove it
 * is wired into the right places; these pin what it does, including the part
 * that matters most -- that it gives up.
 */
class ConstraintRetryTest {

    @Test
    void runsOnceWhenNothingCollides() {
        AtomicInteger attempts = new AtomicInteger();

        String result = ConstraintRetry.retrying(() -> {
            attempts.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void succeedsOnTheAttemptAfterTheCollision() {
        // The whole point: the loser of a race re-reads and gets the value the
        // collision logic would have given it in the first place.
        AtomicInteger attempts = new AtomicInteger();

        String result = ConstraintRetry.retrying(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new DataIntegrityViolationException("slug taken");
            }
            return "slug-2";
        });

        assertThat(result).isEqualTo("slug-2");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void givesUpOnACollisionThatIsNotARace() {
        // A value that will not become free must not loop: retrying forever
        // turns a bad request into a hung thread, and the caller never learns
        // what was wrong.
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> ConstraintRetry.retrying(() -> {
            attempts.incrementAndGet();
            throw new DataIntegrityViolationException("email already registered");
        })).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("email already registered");

        assertThat(attempts).hasValue(ConstraintRetry.DEFAULT_ATTEMPTS);
    }

    @Test
    void letsEveryOtherFailureThrough() {
        // Only the constraint violation is a race worth re-running. Retrying a
        // validation error would just run it three times.
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> ConstraintRetry.retrying(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("not a collision");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(attempts).hasValue(1);
    }
}
