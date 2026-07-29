package com.localmediakit.notification;

/** Lifecycle of one queued notification. */
public enum NotificationStatus {

    /** Waiting for the dispatch job, or waiting out a retry backoff. */
    PENDING,

    /** Handed to the mail provider without error. */
    SENT,

    /** Gave up after the retry budget ran out. Terminal, and kept as a record. */
    FAILED,

    /**
     * Deliberately not sent — the owner's hourly cap was already reached.
     * Recorded rather than dropped so a burst is visible after the fact
     * instead of looking like mail that silently went missing.
     */
    SUPPRESSED
}
