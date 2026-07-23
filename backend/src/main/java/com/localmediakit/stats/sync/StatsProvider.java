package com.localmediakit.stats.sync;

import com.localmediakit.stats.Platform;

/**
 * Fetches current stats for one external account. The twin of
 * {@code EngagementCalculator}: adding a platform is writing a new component,
 * the pipeline never changes (Open/Closed).
 */
public interface StatsProvider {

    Platform platform();

    /**
     * false while the provider's credentials are absent (graceful-enable):
     * the platform then behaves as if sync did not exist (503 on connect).
     */
    default boolean available() {
        return true;
    }

    /** @throws StatsProviderException classified by {@link StatsProviderException.Kind} */
    FetchedStats fetch(String externalId);
}
