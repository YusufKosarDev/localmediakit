package com.localmediakit.stats.engagement;

import com.localmediakit.stats.Platform;
import com.localmediakit.stats.PlatformStats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * TikTok: view-based like YouTube when avg_views is known — TikTok reach is
 * driven by the feed algorithm, not the follower graph — but falls back to the
 * follower-based rate when views are missing, since a follower count always
 * exists while view averages often go unreported.
 */
@Component
public class TikTokEngagementCalculator implements EngagementCalculator {

    @Override
    public Platform platform() {
        return Platform.TIKTOK;
    }

    @Override
    public Optional<BigDecimal> calculate(PlatformStats stats) {
        return EngagementMath.interactions(stats).flatMap(interactions -> {
            Long denominator = (stats.getAvgViews() != null && stats.getAvgViews() > 0)
                    ? stats.getAvgViews()
                    : stats.getFollowers();
            return EngagementMath.percentage(interactions, denominator);
        });
    }
}
