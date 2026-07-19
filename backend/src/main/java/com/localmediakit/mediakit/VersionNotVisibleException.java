package com.localmediakit.mediakit;

/** A rollback target outside the plan's visible version window. */
public class VersionNotVisibleException extends RuntimeException {
    public VersionNotVisibleException() {
        super("This version is outside your plan's history window. Upgrade to PRO for full history.");
    }
}
