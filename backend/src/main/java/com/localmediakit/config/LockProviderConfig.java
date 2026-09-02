package com.localmediakit.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Where the scheduled jobs' lock lives.
 *
 * <p>Separate from {@link SchedulingConfig} because that one is switched off in
 * tests and this one must not be: the lock is a thing to assert on, and a test
 * that wants to prove a second instance is refused needs the provider even
 * though it does not want the scheduler.
 *
 * <p>The lock is in the database because the database is the only thing two
 * instances already share. No Redis, no second service to keep alive, and
 * nothing new that can be down — if the database is unreachable the jobs have
 * nothing to do anyway.
 */
@Configuration
public class LockProviderConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        // Database time, not application time. Two instances
                        // agreeing on when a lease expires matters more than
                        // either being right about the wall clock, and they
                        // cannot both be trusted to have the same one.
                        .usingDbTime()
                        .build());
    }
}
