package com.localmediakit.stats;

import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import com.localmediakit.stats.engagement.EngagementCalculatorRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class StatsService {

    static final Duration TREND_WINDOW = Duration.ofDays(30);

    private final PlatformStatsRepository repository;
    private final MediaKitAccess access;
    private final EngagementCalculatorRegistry calculators;

    public StatsService(PlatformStatsRepository repository,
                        MediaKitAccess access,
                        EngagementCalculatorRegistry calculators) {
        this.repository = repository;
        this.access = access;
        this.calculators = calculators;
    }

    /** Appends a new measurement to the series (never overwrites history). */
    @Transactional
    public PlatformStatView record(String userEmail, Long kitId, RecordStatsRequest request) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        PlatformStats row = new PlatformStats(
                kit.getId(), request.platform(), request.followers(),
                request.avgViews(), request.avgLikes(), request.avgComments());
        repository.save(row);
        return toView(row);
    }

    /**
     * Append path for callers that have already established authorization
     * (the sync pipeline: ownership checked at connect, batch is system-driven).
     */
    @Transactional
    public void recordForKit(Long kitId, RecordStatsRequest request) {
        repository.save(new PlatformStats(
                kitId, request.platform(), request.followers(),
                request.avgViews(), request.avgLikes(), request.avgComments()));
    }

    @Transactional(readOnly = true)
    public List<PlatformStatView> latest(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return latestForKit(kit.getId());
    }

    /**
     * Latest measurement per platform with derived metrics. Also used by the
     * publish flow to freeze the current stats into the snapshot — the public
     * page never computes anything live.
     */
    @Transactional(readOnly = true)
    public List<PlatformStatView> latestForKit(Long kitId) {
        return repository.platformsWithData(kitId).stream()
                .sorted()
                .map(platform -> repository
                        .findFirstByMediaKitIdAndPlatformOrderByRecordedAtDescIdDesc(kitId, platform)
                        .orElseThrow()) // platform came from the same table, a latest row must exist
                .map(this::toView)
                .sorted(Comparator.comparing(PlatformStatView::platform))
                .toList();
    }

    private PlatformStatView toView(PlatformStats latest) {
        BigDecimal engagement = calculators.forPlatform(latest.getPlatform())
                .calculate(latest)
                .orElse(null);
        return new PlatformStatView(
                latest.getPlatform(),
                latest.getFollowers(),
                latest.getAvgViews(),
                latest.getAvgLikes(),
                latest.getAvgComments(),
                engagement,
                followerGrowth(latest),
                latest.getRecordedAt());
    }

    /**
     * 30-day follower growth in percent. Baseline = the most recent row at or
     * before (now - 30d); when the whole series is younger, the series' first
     * row. Null when there is no distinct baseline (single measurement) or the
     * baseline has no followers to grow from.
     */
    private BigDecimal followerGrowth(PlatformStats latest) {
        Instant cutoff = Instant.now().minus(TREND_WINDOW);
        PlatformStats baseline = repository
                .findFirstByMediaKitIdAndPlatformAndRecordedAtLessThanEqualOrderByRecordedAtDescIdDesc(
                        latest.getMediaKitId(), latest.getPlatform(), cutoff)
                .orElseGet(() -> repository
                        .findFirstByMediaKitIdAndPlatformOrderByRecordedAtAscIdAsc(
                                latest.getMediaKitId(), latest.getPlatform())
                        .orElse(latest));
        if (baseline.getId().equals(latest.getId()) || baseline.getFollowers() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(latest.getFollowers() - baseline.getFollowers())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(baseline.getFollowers()), 1, RoundingMode.HALF_UP);
    }
}
