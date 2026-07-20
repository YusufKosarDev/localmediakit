package com.localmediakit.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * On startup: purges throwaway @test.dev accounts and rebuilds the demo
 * account. Disabled in the test profile. Failures are logged, never fatal —
 * a demo-seeding hiccup must not stop the app from booting.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed-on-startup", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final DemoDataService demoDataService;

    public DemoDataInitializer(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int cleaned = demoDataService.cleanupTestUsers();
            if (cleaned > 0) {
                log.info("Removed {} throwaway test account(s)", cleaned);
            }
        } catch (Exception e) {
            log.warn("Test-account cleanup failed: {}", e.getMessage());
        }
        try {
            demoDataService.resetDemo();
        } catch (Exception e) {
            log.warn("Demo reset failed: {}", e.getMessage());
        }
    }
}
