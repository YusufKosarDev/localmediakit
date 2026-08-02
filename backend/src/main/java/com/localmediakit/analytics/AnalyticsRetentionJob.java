package com.localmediakit.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the analytics rollup on the same discipline as the other batches:
 * fixedDelay so runs cannot pile up, and an initial delay so it stays dormant
 * during startup.
 *
 * <p>The interval is long because nothing waits on this. Views become eligible
 * for folding a day at a time, so running hourly would spend most of its passes
 * discovering there is nothing to do.
 */
@Component
public class AnalyticsRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRetentionJob.class);

    private final AnalyticsRetentionService service;

    public AnalyticsRetentionJob(AnalyticsRetentionService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.analytics.retention-job-interval-ms:21600000}",
            initialDelayString = "${app.analytics.retention-job-initial-delay-ms:120000}")
    public void run() {
        int folded = service.runRetentionBatch();
        if (folded > 0) {
            log.info("Analytics retention folded {} kit-day bucket(s)", folded);
        }
    }
}
