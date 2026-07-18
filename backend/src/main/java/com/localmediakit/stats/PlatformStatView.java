package com.localmediakit.stats;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Latest measurement of one platform enriched with the derived metrics:
 * engagement rate (per-platform strategy) and 30-day follower growth (trend).
 * Both are null when they cannot be computed.
 */
public record PlatformStatView(
        Platform platform,
        long followers,
        Long avgViews,
        Long avgLikes,
        Long avgComments,
        BigDecimal engagementRate,
        BigDecimal followerGrowth30d,
        Instant recordedAt) {
}
