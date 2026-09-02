package com.localmediakit.domain;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prevents a batch from being re-entered while a previous run is still in
 * progress, within this JVM.
 *
 * <p>The other half of that guarantee is {@code @SchedulerLock} on the job
 * classes, which holds a row in the database and is what stops a second
 * instance running the same batch on the same tick. The two are not
 * alternatives and the earlier version of this comment was wrong to present
 * them that way: they answer different questions at different layers.
 *
 * <p>ShedLock guards the schedule. This guards the method. Every batch here is
 * also callable directly — by a test, and by the manual-sync endpoint — and
 * those callers never pass through the scheduler, so removing this would leave
 * the service re-entrant on exactly the paths that do not hold the lock. It is
 * also free: an AtomicBoolean answers before a database round-trip is made.
 */
@Component
public class ReentrancyGuard {

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Runs {@code task} only if no run is currently in progress.
     *
     * @return true if the task ran, false if it was skipped (already running).
     */
    public boolean tryRun(Runnable task) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            running.set(false);
        }
    }
}
