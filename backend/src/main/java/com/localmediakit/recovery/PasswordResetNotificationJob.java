package com.localmediakit.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the password-reset outbox on the same scheduling discipline as the
 * other background jobs: fixedDelay so runs cannot pile up, and an initial
 * delay so it stays dormant during startup and short-lived test contexts.
 *
 * <p>The interval is the shortest in the application, because this is the job
 * somebody is actually sitting in front of their inbox waiting for. It is also
 * the job least at risk from a host that sleeps when idle: the request that
 * queues the work has just woken the instance, so the first attempt runs while
 * it is still up.
 */
@Component
public class PasswordResetNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetNotificationJob.class);

    private final PasswordResetNotificationService service;

    public PasswordResetNotificationJob(PasswordResetNotificationService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.password-reset.job-interval-ms:30000}",
            initialDelayString = "${app.password-reset.job-initial-delay-ms:15000}")
    public void run() {
        int attempted = service.runDispatchBatch();
        if (attempted > 0) {
            log.info("Password reset batch attempted {} message(s)", attempted);
        }
    }
}
