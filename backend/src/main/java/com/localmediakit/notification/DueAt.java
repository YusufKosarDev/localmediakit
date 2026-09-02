package com.localmediakit.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The moment an outbox row becomes due, written so the database cannot move it
 * into the future.
 *
 * <p>Every outbox here queues its first attempt "due immediately" by storing
 * the current instant, and every dispatcher asks for rows whose due time is
 * {@code <= now}. That reads as obviously correct and is not, because the value
 * that comes back out of the column is not the value that went in.
 *
 * <p>{@link Instant} carries nanoseconds; the columns are {@code TIMESTAMP(6)},
 * which carries microseconds. The conversion <b>rounds</b> rather than
 * truncates, so an instant ending .816885500 is stored as .816886 — half a
 * microsecond <em>later</em> than the moment it was meant to record. A batch
 * that runs inside that window asks for rows due at or before a "now" that is
 * genuinely earlier than the stored value, finds nothing, and leaves a row
 * marked due-immediately sitting in the queue.
 *
 * <p>In production the next tick is thirty seconds away and picks it up, so the
 * cost is one missed cycle on a mail somebody is waiting for. In tests, which
 * queue a row and drain it in the same breath, it is a failure that appears in
 * perhaps two runs out of three and names a different test each time.
 *
 * <p>Truncating first makes the stored value exactly the value in memory, and
 * never later than the instant it represents. Sub-microsecond precision is not
 * something a job scheduled in minutes has any use for.
 */
public final class DueAt {

    private DueAt() {
    }

    /** Now, at the precision the column can actually hold. */
    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
