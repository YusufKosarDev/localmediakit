package com.localmediakit.stats.engagement;

import com.localmediakit.stats.Platform;
import com.localmediakit.stats.PlatformStats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * YouTube: (avg_likes + avg_comments) / avg_views x 100 — view-based, because
 * subscriber counts say little about who actually watches.
 */
@Component
public class YouTubeEngagementCalculator implements EngagementCalculator {

    @Override
    public Platform platform() {
        return Platform.YOUTUBE;
    }

    @Override
    public Optional<BigDecimal> calculate(PlatformStats stats) {
        return EngagementMath.interactions(stats)
                .flatMap(interactions -> EngagementMath.percentage(interactions, stats.getAvgViews()));
    }
}
