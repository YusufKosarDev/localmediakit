package com.localmediakit.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the notification outbox on the same scheduling discipline as the
 * other background jobs: fixedDelay so runs never pile up, and an initial
 * delay so it stays dormant during startup and short-lived test contexts.
 *
 * <p>The interval is short because this is the one job a user waits on — a
 * brand's message should land in their inbox in about a minute, not an hour.
 */
@Component
public class LeadNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(LeadNotificationJob.class);

    private final LeadNotificationService service;

    public LeadNotificationJob(LeadNotificationService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.notifications.job-interval-ms:60000}",
            initialDelayString = "${app.notifications.job-initial-delay-ms:20000}")
    public void run() {
        int attempted = service.runDispatchBatch();
        if (attempted > 0) {
            log.info("Lead notification batch attempted {} message(s)", attempted);
        }
    }
}
