package com.localmediakit.shared;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.function.Supplier;

/**
 * Runs a unit of work again when the database rejects it for colliding with
 * another one.
 *
 * <p>Three places in this codebase pick a value by reading what is already
 * there and then writing: the next free slug, the next version number, the
 * source row for a platform. Between the read and the write another request can
 * take the value, and the only thing that notices is the unique constraint. That
 * is the right thing to notice it -- the constraint is the authority, and moving
 * the decision into the application would mean locking rows on the read path to
 * defend against something that almost never happens.
 *
 * <p>What was wrong was the response. The violation escaped as a 500, so two
 * people creating a kit with the same title at the same time produced one kit
 * and one server error, when the collision logic that already exists would have
 * given the second one a suffix. Retrying re-runs the read, which now sees the
 * committed row and picks the next value.
 *
 * <p>Each attempt must be its own transaction: a JPA transaction that has hit a
 * constraint violation is finished, and continuing in it fails differently. So
 * the supplier passed here is expected to open one -- in this codebase that
 * means a TransactionTemplate, which is also how these services already avoid
 * Spring's self-invocation trap.
 *
 * <p>The attempt limit exists because retrying cannot fix every violation.
 * A genuinely duplicate email is a collision that will still be there on the
 * third try, and looping on it would turn a bad request into a hung thread.
 */
public final class ConstraintRetry {

    /**
     * Attempts needed scales with simultaneous contenders, not with anything
     * else: every round has exactly one winner, so N requests racing for the
     * same value need N attempts before the last one finds a free candidate.
     * Five covers the contention these paths can realistically see -- a
     * double-clicked button, a couple of people naming a kit the same thing --
     * with room to spare.
     *
     * <p>It is bounded rather than open on purpose. Retrying cannot fix every
     * violation: a genuinely duplicate value will still be taken on the tenth
     * try, and looping on it turns a request the caller should hear about into
     * a thread that never answers. Past this point the exception is the honest
     * result, and {@code ApiExceptionHandler} turns it into a 409 rather than
     * a 500 -- "that value is taken", which is true, instead of "this server
     * is broken", which is not.
     */
    public static final int DEFAULT_ATTEMPTS = 5;

    private ConstraintRetry() {
    }

    public static <T> T retrying(Supplier<T> attempt) {
        return retrying(DEFAULT_ATTEMPTS, attempt);
    }

    public static <T> T retrying(int maxAttempts, Supplier<T> attempt) {
        DataIntegrityViolationException lastFailure = null;
        for (int remaining = maxAttempts; remaining > 0; remaining--) {
            try {
                return attempt.get();
            } catch (DataIntegrityViolationException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }
}
