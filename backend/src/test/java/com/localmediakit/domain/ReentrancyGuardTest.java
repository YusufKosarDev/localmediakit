package com.localmediakit.domain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReentrancyGuardTest {

    @Test
    void secondConcurrentRunIsSkipped() throws Exception {
        ReentrancyGuard guard = new ReentrancyGuard();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstRan = new AtomicBoolean(false);

        Thread first = new Thread(() -> guard.tryRun(() -> {
            firstRan.set(true);
            inside.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        first.start();

        assertTrue(inside.await(2, TimeUnit.SECONDS), "first run should start");

        // While the first run is still inside, a second run is refused.
        AtomicBoolean secondBody = new AtomicBoolean(false);
        boolean secondRan = guard.tryRun(() -> secondBody.set(true));
        assertFalse(secondRan, "second run should be skipped");
        assertFalse(secondBody.get(), "second body should never execute");

        release.countDown();
        first.join(2000);
        assertTrue(firstRan.get());

        // After the first finishes, the guard is free again.
        assertTrue(guard.tryRun(() -> { }), "guard should be reusable after release");
    }
}
