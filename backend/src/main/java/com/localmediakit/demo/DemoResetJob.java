package com.localmediakit.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly demo reset so a reviewer always lands on clean, populated data even
 * after someone has edited the demo account. Disabled in tests.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed-on-startup", havingValue = "true", matchIfMissing = true)
public class DemoResetJob {

    private static final Logger log = LoggerFactory.getLogger(DemoResetJob.class);

    private final DemoDataService demoDataService;

    public DemoResetJob(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    /** Every day at 03:00 UTC. */
    @Scheduled(cron = "${app.demo.reset-cron:0 0 3 * * *}", zone = "UTC")
    public void nightlyReset() {
        try {
            demoDataService.resetDemo();
            log.info("Nightly demo reset complete");
        } catch (Exception e) {
            log.warn("Nightly demo reset failed: {}", e.getMessage());
        }
    }
}
