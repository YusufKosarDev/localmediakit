package com.localmediakit.stats.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes connected stat sources (same scheduling discipline as
 * the domain verification job: fixedDelay so runs never pile up, initial delay
 * to stay dormant during startup and short-lived test contexts). The hourly
 * tick is cheap: per-source cadence is enforced by last_synced_at, so a source
 * is actually fetched about once a day.
 */
@Component
public class StatsSyncJob {

    private static final Logger log = LoggerFactory.getLogger(StatsSyncJob.class);

    private final StatsSyncService service;

    public StatsSyncJob(StatsSyncService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.statsync.job-interval-ms:3600000}",
            initialDelayString = "${app.statsync.job-initial-delay-ms:45000}")
    public void run() {
        int attempted = service.runSyncBatch();
        if (attempted > 0) {
            log.info("Stats sync batch attempted {} source(s)", attempted);
        }
    }
}
