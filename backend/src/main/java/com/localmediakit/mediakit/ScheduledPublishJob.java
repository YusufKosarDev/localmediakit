package com.localmediakit.mediakit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ticks often, because the interval is the worst-case lateness a creator sees:
 * a page scheduled for 09:00 that goes live at 09:59 has missed the point of
 * being scheduled at all.
 */
@Component
public class ScheduledPublishJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublishJob.class);

    private final ScheduledPublishService service;

    public ScheduledPublishJob(ScheduledPublishService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.scheduled-publish.job-interval-ms:60000}",
            initialDelayString = "${app.scheduled-publish.job-initial-delay-ms:30000}")
    public void run() {
        int published = service.runDueBatch();
        if (published > 0) {
            log.info("Scheduled publish batch put {} kit(s) live", published);
        }
    }
}
