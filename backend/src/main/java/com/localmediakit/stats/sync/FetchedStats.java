package com.localmediakit.stats.sync;

/** What a provider returns for one account; maps 1:1 onto a platform_stats row. */
public record FetchedStats(
        long followers,
        Long avgViews,
        Long avgLikes,
        Long avgComments) {
}
