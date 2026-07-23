package com.localmediakit.stats.sync;

/**
 * A provider fetch failure, classified for the sync pipeline:
 *  - NOT_FOUND: the external account does not exist (caller's input is wrong);
 *  - QUOTA: the provider's API budget is exhausted — the batch must stop;
 *  - TRANSIENT: network/5xx — retry on the next cadence.
 */
public class StatsProviderException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        QUOTA,
        TRANSIENT
    }

    private final Kind kind;

    public StatsProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
