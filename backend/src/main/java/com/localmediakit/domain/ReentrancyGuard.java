package com.localmediakit.domain;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prevents a scheduled task from running while a previous run is still in
 * progress. On this deployment (single Render instance) this in-JVM guard is
 * sufficient; a multi-instance deployment would swap it for a DB lock such as
 * ShedLock. Kept as its own unit so the overlap behaviour is testable in
 * isolation.
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
