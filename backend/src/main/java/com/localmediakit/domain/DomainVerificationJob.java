package com.localmediakit.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically drives domain verification. fixedDelay (not fixedRate) means the
 * next run starts only after the previous one finishes, so runs never pile up;
 * the initial delay keeps the job dormant during application startup (and the
 * short-lived test context). On Render's single free instance this runs while
 * the app is awake; when the instance sleeps the job simply pauses, which is
 * acceptable for a "coming soon" feature.
 */
@Component
public class DomainVerificationJob {

    private static final Logger log = LoggerFactory.getLogger(DomainVerificationJob.class);

    private final DomainVerificationService service;

    public DomainVerificationJob(DomainVerificationService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.domains.job-interval-ms:60000}",
            initialDelayString = "${app.domains.job-initial-delay-ms:30000}")
    public void run() {
        int processed = service.runVerificationBatch();
        if (processed > 0) {
            log.info("Domain verification batch processed {} domain(s)", processed);
        }
    }
}
