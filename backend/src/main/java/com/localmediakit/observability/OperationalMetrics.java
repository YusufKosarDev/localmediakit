package com.localmediakit.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * The short list of things whose silence is dangerous.
 *
 * <p>Several failure modes in this system are deliberately non-fatal: a
 * revalidation that does not reach the frontend, a lead notification the mail
 * provider rejects, a stats batch that stops on an exhausted quota. Each of
 * those decisions is correct — none of them should cost a user their request or
 * their data — but "handled" was quietly being used to mean "unobserved". The
 * outbox marks a row FAILED and moves on; nothing ever looked at the rows.
 *
 * <p>So the counters live here rather than as string literals scattered across
 * the services that emit them. Reading this class tells you what this system
 * considers a problem, which is exactly the question someone has when they are
 * asked whether it is working. It also keeps the metric names from drifting: a
 * typo in a literal at a call site produces a second, empty time series, and
 * the graph that stays flat is indistinguishable from the thing never failing.
 */
@Component
public class OperationalMetrics {

    /** Publishes that completed. The one number that says the product is being used. */
    private final Counter publishes;

    /**
     * A publish whose edge revalidation did not land. The snapshot is committed
     * and correct, but the public page may still serve the previous one until
     * something else revalidates it — the single most misleading state this
     * system can be in, because everything looks successful from the dashboard.
     */
    private final Counter revalidationFailures;

    /** Notifications the mail provider accepted. */
    private final Counter leadNotificationsSent;

    /**
     * Notifications that exhausted their retry budget. Each one is a creator who
     * was never told a brand contacted them; the lead is still in their inbox,
     * so this is lost promptness rather than lost data.
     */
    private final Counter leadNotificationFailures;

    /**
     * The stats batch stopped early because the upstream quota ran out. Sources
     * stay stale until the quota resets, and nothing else reports it.
     */
    private final Counter statsSyncQuotaExhaustions;

    /** A single source that failed to refresh. Filed on the source as lastError too. */
    private final Counter statsSyncFailures;

    /**
     * A reset link that was issued but never delivered. Invisible from the
     * outside by design -- the endpoint says nothing either way -- so this
     * counter is the only place it shows up. Someone locked out of their
     * account, waiting for a mail that is not coming.
     */
    private final Counter passwordResetMailsSent;
    private final Counter passwordResetMailFailures;

    /** A page that went live at the moment its creator chose. */
    private final Counter scheduledPublishes;

    /**
     * A scheduled publish that did not happen. Invisible unless the creator
     * goes looking: they set it and walked away believing the page is live.
     */
    private final Counter scheduledPublishFailures;

    public OperationalMetrics(MeterRegistry registry) {
        this.publishes = Counter.builder("localmediakit.publish.completed")
                .description("Media kit publishes that produced a new active version")
                .register(registry);
        this.revalidationFailures = Counter.builder("localmediakit.revalidation.failed")
                .description("Publishes whose edge revalidation did not succeed")
                .register(registry);
        this.leadNotificationsSent = Counter.builder("localmediakit.lead.notification.sent")
                .description("Lead notification emails accepted by the mail provider")
                .register(registry);
        this.leadNotificationFailures = Counter.builder("localmediakit.lead.notification.failed")
                .description("Lead notifications that exhausted their retry budget")
                .register(registry);
        this.statsSyncQuotaExhaustions = Counter.builder("localmediakit.statsync.quota_exhausted")
                .description("Stats sync batches aborted by an upstream quota limit")
                .register(registry);
        this.statsSyncFailures = Counter.builder("localmediakit.statsync.source_failed")
                .description("Stat sources that failed to refresh")
                .register(registry);
        this.passwordResetMailsSent = Counter.builder("localmediakit.password_reset.mail_sent")
                .description("Password reset links accepted by the mail provider")
                .register(registry);
        this.passwordResetMailFailures = Counter.builder("localmediakit.password_reset.mail_failed")
                .description("Password reset links that exhausted their retry budget")
                .register(registry);
        this.scheduledPublishes = Counter.builder("localmediakit.scheduled_publish.completed")
                .description("Kits published at their scheduled moment")
                .register(registry);
        this.scheduledPublishFailures = Counter.builder("localmediakit.scheduled_publish.failed")
                .description("Scheduled publishes that did not go out")
                .register(registry);
    }

    public void publishCompleted() {
        publishes.increment();
    }

    public void revalidationFailed() {
        revalidationFailures.increment();
    }

    public void leadNotificationSent() {
        leadNotificationsSent.increment();
    }

    public void leadNotificationFailed() {
        leadNotificationFailures.increment();
    }

    public void statsSyncQuotaExhausted() {
        statsSyncQuotaExhaustions.increment();
    }

    public void statsSyncSourceFailed() {
        statsSyncFailures.increment();
    }

    public void passwordResetMailSent() {
        passwordResetMailsSent.increment();
    }

    public void passwordResetMailFailed() {
        passwordResetMailFailures.increment();
    }

    public void scheduledPublishCompleted() {
        scheduledPublishes.increment();
    }

    public void scheduledPublishFailed() {
        scheduledPublishFailures.increment();
    }
}
