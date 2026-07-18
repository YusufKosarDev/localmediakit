package com.localmediakit.stats.engagement;

import com.localmediakit.stats.Platform;
import com.localmediakit.stats.PlatformStats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** Instagram: (avg_likes + avg_comments) / followers x 100 — follower-based. */
@Component
public class InstagramEngagementCalculator implements EngagementCalculator {

    @Override
    public Platform platform() {
        return Platform.INSTAGRAM;
    }

    @Override
    public Optional<BigDecimal> calculate(PlatformStats stats) {
        return EngagementMath.interactions(stats)
                .flatMap(interactions -> EngagementMath.percentage(interactions, stats.getFollowers()));
    }
}
