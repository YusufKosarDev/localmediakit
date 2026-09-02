package com.localmediakit.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guarantee that makes a second instance safe.
 *
 * <p>Every scheduled job in this application takes rows, acts on the world and
 * writes the result back. Two instances running one on the same tick does not
 * fail — it sends a brand notification twice, folds an analytics day twice,
 * publishes a scheduled kit twice. Nothing is logged as wrong, because from
 * each instance's point of view nothing was.
 *
 * <p>A second JVM cannot be started inside a test, so the thing being tested
 * is the mechanism the two instances would share: the lock provider, holding a
 * row in the same database. A second acquisition of a held lock is what a
 * second instance would attempt, and it must come back empty.
 */
@SpringBootTest
class SchedulerLockTest {

    @Autowired
    private LockProvider lockProvider;

    private static LockConfiguration lockFor(String name) {
        return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(5), Duration.ZERO);
    }

    @Test
    void aHeldLockIsRefusedToTheNextCaller() {
        Optional<SimpleLock> first = lockProvider.lock(lockFor("test-job"));
        assertThat(first).as("the first caller takes the lock").isPresent();

        // This is the second instance's attempt, on the same tick.
        Optional<SimpleLock> second = lockProvider.lock(lockFor("test-job"));
        assertThat(second).as("a second instance must be refused").isEmpty();

        first.get().unlock();
    }

    @Test
    void theLockIsReleasedForTheNextTick() {
        lockProvider.lock(lockFor("release-job")).orElseThrow().unlock();

        // lockAtLeastFor is zero here, so the next tick may take it immediately.
        Optional<SimpleLock> next = lockProvider.lock(lockFor("release-job"));
        assertThat(next).as("a released lock is available again").isPresent();
        next.get().unlock();
    }

    @Test
    void differentJobsDoNotBlockEachOther() {
        Optional<SimpleLock> outbox = lockProvider.lock(lockFor("job-a"));
        Optional<SimpleLock> retention = lockProvider.lock(lockFor("job-b"));

        assertThat(outbox).isPresent();
        assertThat(retention).as("a lock is per job, not global").isPresent();

        outbox.get().unlock();
        retention.get().unlock();
    }

}
