package com.localmediakit.stats;

import java.math.BigDecimal;

public record StatsResponse(
        String platform,
        long followers,
        Long avgViews,
        Long avgLikes,
        Long avgComments,
        BigDecimal engagementRate,
        BigDecimal followerGrowth30d,
        String recordedAt) {

    public static StatsResponse from(PlatformStatView view) {
        return new StatsResponse(
                view.platform().name(),
                view.followers(),
                view.avgViews(),
                view.avgLikes(),
                view.avgComments(),
                view.engagementRate(),
                view.followerGrowth30d(),
                view.recordedAt().toString());
    }
}
