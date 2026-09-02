package com.localmediakit.user;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the address-verification outbox.
 *
 * <p>Same discipline as the other outbox jobs, and the same short interval as
 * the password-reset one for the same reason: somebody is sitting in front of
 * an inbox waiting for this, and the request that queued it has just woken the
 * instance, so the first attempt runs while it is still up.
 */
@Component
public class EmailChangeNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeNotificationJob.class);

    private final EmailChangeNotificationService service;

    public EmailChangeNotificationJob(EmailChangeNotificationService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.email-change.job-interval-ms:30000}",
            initialDelayString = "${app.email-change.job-initial-delay-ms:15000}")
    @SchedulerLock(name = "emailChangeNotifications", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void run() {
        int attempted = service.runDispatchBatch();
        if (attempted > 0) {
            log.info("Email change batch attempted {} message(s)", attempted);
        }
    }
}
