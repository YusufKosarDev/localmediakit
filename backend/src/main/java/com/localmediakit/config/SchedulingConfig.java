package com.localmediakit.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Scheduling, and the lock that makes a second instance safe.
 *
 * <p>Every scheduled job in this application reads rows, does something to the
 * world, and writes the result back. Two instances running the same job on the
 * same tick is not a crash — it is the outbox mailing a brand notification
 * twice, the retention job folding the same day twice, a scheduled kit
 * publishing twice. Nothing fails; the work is simply done more than once, and
 * the only evidence is in somebody else's inbox. That is the failure mode worth
 * paying to remove.
 *
 * <p>The lock lives in the database because the database is the only thing the
 * instances already share. No Redis, no second service to keep alive, and no
 * new thing that can be down — if the database is unreachable the jobs have
 * nothing to do anyway.
 *
 * <p>{@code defaultLockAtMostFor} is the backstop for the case the guard exists
 * for: an instance that takes the lock and then dies without releasing it. The
 * lease expires on its own, so a crash costs one interval of lateness rather
 * than a job that never runs again. Each job overrides it with a value sized to
 * its own work.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        // Database time, not application time. Two instances
                        // agreeing on when a lease expires matters more than
                        // either of them being right about the wall clock, and
                        // they cannot both be trusted to have the same one.
                        .usingDbTime()
                        .build());
    }
}
