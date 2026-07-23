package com.localmediakit.stats.sync;

public class SyncCooldownException extends RuntimeException {
    public SyncCooldownException() {
        super("Synced very recently. Please wait a moment and try again.");
    }
}
