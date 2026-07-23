package com.localmediakit.stats.sync;

public class SyncNotConfiguredException extends RuntimeException {
    public SyncNotConfiguredException() {
        super("Stats sync is not configured for this platform");
    }
}
