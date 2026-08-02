package com.localmediakit.analytics;

import com.localmediakit.domain.ReentrancyGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Folds old page views into daily rows and deletes the raw ones behind them.
 *
 * <p>page_views is append-only and had no retention: it grew with traffic
 * forever, on a free Postgres tier, and kept per-visit rows long after anyone
 * would act on one. That is a bill and a data-protection answer nobody wanted
 * to have to give.
 *
 * <p>Deleting them was not free, though, which is why this rolls up first. Total
 * views and unique visitors are counted over the whole table, so a plain
 * DELETE would have walked a creator's lifetime numbers backwards with no
 * explanation. After the rollup the totals are read as "rolled-up plus what is
 * still inside the window" and do not move at all.
 *
 * <p>What does change is the referrer and device breakdowns, which are computed
 * from raw rows and therefore become "the retention window" rather than "all
 * time". That is deliberate and worth saying out loud rather than hiding in a
 * migration: a referrer from two years ago is not something anyone acts on, and
 * storing every visit forever to keep such a number exact is the wrong trade.
 */
@Service
public class AnalyticsRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRetentionService.class);

    private final PageViewRepository pageViewRepository;
    private final PageViewDailyRepository dailyRepository;
    private final TransactionTemplate transactionTemplate;
    private final Duration retention;
    private final int batchSize;

    /** Its own instance: this batch and the other jobs must not block each other. */
    private final ReentrancyGuard batchGuard = new ReentrancyGuard();

    public AnalyticsRetentionService(PageViewRepository pageViewRepository,
                                     PageViewDailyRepository dailyRepository,
                                     TransactionTemplate transactionTemplate,
                                     @Value("${app.analytics.retention-days:90}") int retentionDays,
                                     @Value("${app.analytics.retention-batch-size:200}") int batchSize) {
        this.pageViewRepository = pageViewRepository;
        this.dailyRepository = dailyRepository;
        this.transactionTemplate = transactionTemplate;
        this.retention = Duration.ofDays(retentionDays);
        this.batchSize = batchSize;
    }

    /**
     * @return how many (kit, day) buckets were folded away, or -1 if skipped
     *         because a previous run is still going.
     */
    public int runRetentionBatch() {
        int[] folded = {0};
        boolean ran = batchGuard.tryRun(() -> {
            // Aligned to midnight UTC rather than "now minus 90 days" so that no
            // bucket can straddle it. An unaligned cutoff falls in the middle of
            // a day: the count would cover the morning and the delete would take
            // the whole day, silently dropping the afternoon's views. Aligning
            // removes the case instead of handling it.
            Instant cutoff = LocalDate.now(ZoneOffset.UTC)
                    .minusDays(retention.toDays())
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
            List<Object[]> buckets = transactionTemplate.execute(status ->
                    pageViewRepository.bucketsOlderThan(cutoff, batchSize));
            if (buckets == null || buckets.isEmpty()) {
                return;
            }
            for (Object[] bucket : buckets) {
                Long kitId = ((Number) bucket[0]).longValue();
                LocalDate day = ((Date) bucket[1]).toLocalDate();
                long views = ((Number) bucket[2]).longValue();
                long uniques = ((Number) bucket[3]).longValue();
                try {
                    // One transaction per bucket, like the notification outbox:
                    // a single bad day must not roll back the ones already
                    // folded, and the rollup and the delete for one day have to
                    // commit together or the numbers are wrong either way --
                    // rolled up but not deleted double-counts, deleted but not
                    // rolled up loses the views outright.
                    transactionTemplate.executeWithoutResult(status -> fold(kitId, day, views, uniques));
                    folded[0]++;
                } catch (RuntimeException e) {
                    log.warn("Analytics retention skipped kit {} day {}: {}", kitId, day, e.getMessage());
                }
            }
            log.info("Analytics retention folded {} kit-day bucket(s) older than {}", folded[0], cutoff);
        });
        return ran ? folded[0] : -1;
    }

    private void fold(Long kitId, LocalDate day, long views, long uniques) {
        dailyRepository.findByMediaKitIdAndViewDay(kitId, day)
                .ifPresentOrElse(
                        // Adding rather than replacing: a day can be folded more
                        // than once if views for it were still arriving when the
                        // first pass ran, and replacing would drop the earlier half.
                        existing -> existing.add(views, uniques),
                        () -> dailyRepository.save(new PageViewDaily(kitId, day, views, uniques)));
        pageViewRepository.deleteForKitOnDay(kitId, day);
    }
}
