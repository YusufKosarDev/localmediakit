package com.localmediakit.stats.engagement;

import com.localmediakit.stats.PlatformStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** Shared arithmetic for the calculators; formulas themselves live per strategy. */
final class EngagementMath {

    private EngagementMath() {
    }

    /**
     * avg_likes + avg_comments, or empty when NEITHER is present (no
     * interaction data at all is "unknown", not zero engagement).
     */
    static Optional<Long> interactions(PlatformStats stats) {
        if (stats.getAvgLikes() == null && stats.getAvgComments() == null) {
            return Optional.empty();
        }
        long likes = stats.getAvgLikes() == null ? 0 : stats.getAvgLikes();
        long comments = stats.getAvgComments() == null ? 0 : stats.getAvgComments();
        return Optional.of(likes + comments);
    }

    /** interactions / denominator * 100, 2 decimals; empty for a non-positive denominator. */
    static Optional<BigDecimal> percentage(long interactions, Long denominator) {
        if (denominator == null || denominator <= 0) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(interactions)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP));
    }
}
