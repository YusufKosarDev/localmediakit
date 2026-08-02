package com.localmediakit.migration;

import com.localmediakit.analytics.AnalyticsRetentionService;
import com.localmediakit.analytics.PageView;
import com.localmediakit.analytics.PageViewRepository;
import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitRepository;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The few things H2 cannot answer for.
 *
 * <p>The rest of the suite runs on H2 in PostgreSQL compatibility mode, which is
 * a good trade: no Docker, no secrets, a suite that finishes in a minute. It is
 * a compatibility mode, though, and this project has already paid once for
 * believing it — V17 exists because a migration behaved differently against real
 * data on a real server than it did here.
 *
 * <p>So this class is deliberately small. It does not re-run the suite against
 * Postgres; it covers the places where the database itself is the thing under
 * test: the migrations, the hand-written native SQL, and the constraint
 * behaviour the retry logic depends on. Everything else is application code that
 * a second database would exercise identically and slowly.
 *
 * <p>Tagged so `mvn test` skips it. A laptop without a Docker daemon must still
 * be able to run the build, which is the property the CI comment claims.
 */
@Tag("postgres")
@Testcontainers
@SpringBootTest
class PostgresCompatibilityTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MediaKitRepository mediaKitRepository;

    @Autowired
    private PageViewRepository pageViewRepository;

    @Autowired
    private AnalyticsRetentionService retentionService;

    @Test
    void everyMigrationAppliesToARealPostgres() {
        // Reaching this point means the context started, which means Flyway ran
        // all of them. Asserting on a table keeps that from being an accident of
        // an empty test.
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(userRepository.count()).isNotNegative();
        assertThat(pageViewRepository.count()).isNotNegative();
    }

    @Test
    void theHandWrittenAnalyticsSqlRunsOnPostgresToo() {
        // These are nativeQuery, so they are the one part of the data layer
        // Hibernate does not translate for us: cast(... as date), the grouping
        // and the limit are written against a dialect rather than derived from
        // one. H2 accepting them says nothing about Postgres.
        Long kitId = aKitId("pg-native-sql");
        pageViewRepository.save(new PageView(kitId, "pg-native-sql", "pg-visitor-a", "google.com",
                "DESKTOP", Instant.now().minus(200, ChronoUnit.DAYS)));
        pageViewRepository.save(new PageView(kitId, "pg-native-sql", "pg-visitor-b", null,
                "MOBILE", Instant.now()));

        assertThat(pageViewRepository.dailyCounts(kitId, Instant.now().minus(30, ChronoUnit.DAYS)))
                .hasSize(1);
        assertThat(pageViewRepository.topReferrers(kitId)).isNotEmpty();
        assertThat(pageViewRepository.deviceBreakdown(kitId)).isNotEmpty();
        assertThat(pageViewRepository.bucketsOlderThan(Instant.now().minus(90, ChronoUnit.DAYS), 200))
                .hasSize(1);
    }

    @Test
    void theRetentionRollupWorksAgainstTheRealDatabase() {
        Long kitId = aKitId("pg-retention");
        pageViewRepository.save(new PageView(kitId, "pg-retention", "pg-old-visitor", null,
                "DESKTOP", Instant.now().minus(400, ChronoUnit.DAYS)));

        assertThat(retentionService.runRetentionBatch()).isPositive();
        // The delete is native SQL keyed on cast(viewed_at as date); if the
        // dialects disagreed the rows would survive and this would be zero.
        assertThat(pageViewRepository.bucketsOlderThan(Instant.now().minus(90, ChronoUnit.DAYS), 200))
                .isEmpty();
    }

    @Test
    void aDuplicateSlugIsRefusedByPostgresTheSameWayItIsByH2() {
        // ConstraintRetry catches DataIntegrityViolationException. Spring maps
        // each driver's error to that type separately, so "H2 raises it" is not
        // evidence that the production database does.
        User owner = userRepository.save(new User("pg-constraint@example.com", "hash", "Uretici"));
        mediaKitRepository.saveAndFlush(newKit(owner, "pg-cakisan-slug"));

        assertThatThrownBy(() -> mediaKitRepository.saveAndFlush(newKit(owner, "pg-cakisan-slug")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** Each test owns its rows, so nothing here depends on execution order. */
    private Long aKitId(String name) {
        User owner = userRepository.save(new User(name + "@example.com", "hash", "Uretici"));
        return mediaKitRepository.save(newKit(owner, name)).getId();
    }

    private MediaKit newKit(User owner, String slug) {
        return new MediaKit(owner.getId(), slug, "PG Uyum Kiti", null, null,
                "light", "violet", "classic", "tr");
    }
}
