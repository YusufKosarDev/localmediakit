package com.localmediakit.stats.sync;

public record SyncSourceResponse(
        String platform,
        String externalId,
        String lastSyncedAt,
        String lastError) {

    public static SyncSourceResponse from(StatsSource source) {
        return new SyncSourceResponse(
                source.getPlatform().name(),
                source.getExternalId(),
                source.getLastSyncedAt() == null ? null : source.getLastSyncedAt().toString(),
                source.getLastError());
    }
}
