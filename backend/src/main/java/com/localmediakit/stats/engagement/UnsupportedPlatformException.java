package com.localmediakit.stats.engagement;

import com.localmediakit.stats.Platform;

/** A platform exists in the enum but no calculator was registered for it. */
public class UnsupportedPlatformException extends RuntimeException {
    public UnsupportedPlatformException(Platform platform) {
        super("No engagement calculator registered for platform " + platform);
    }
}
