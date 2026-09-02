package com.localmediakit.config;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns the scheduler on, and the lock that goes with it.
 *
 * <p>Every scheduled job in this application reads rows, does something to the
 * world, and writes the result back. Two instances running the same job on the
 * same tick is not a crash — it is the outbox mailing a brand notification
 * twice, the retention job folding the same day twice, a scheduled kit
 * publishing twice. Nothing fails; the work is simply done more than once, and
 * the only evidence is in somebody else's inbox. {@code @SchedulerLock} on each
 * job is what stops that, backed by {@link LockProviderConfig}.
 *
 * <p><b>Off during tests</b>, via {@code app.scheduling.enabled=false} in the
 * test resources. Not for speed: the tests and the scheduler contend for the
 * same rows. Spring caches one application context per configuration and the
 * datasource is one named in-memory database with {@code DB_CLOSE_DELAY=-1},
 * so <em>every</em> cached context in the JVM shares <em>one</em> database —
 * and a job ticking in a context left over from an earlier test class will
 * happily drain an outbox the class currently running is asserting on. That
 * failure is invisible when a class is run alone and appears in the full suite,
 * which is the least useful way for a test to break.
 *
 * <p>Nothing is lost by turning it off. No test asks whether Spring can call a
 * method on a timer; they call the batches directly, which is also the only way
 * to assert on what a batch did.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
