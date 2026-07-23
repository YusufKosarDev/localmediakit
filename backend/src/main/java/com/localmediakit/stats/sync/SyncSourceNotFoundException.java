package com.localmediakit.stats.sync;

public class SyncSourceNotFoundException extends RuntimeException {
    public SyncSourceNotFoundException() {
        super("Stats source not found");
    }
}
