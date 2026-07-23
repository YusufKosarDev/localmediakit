package com.localmediakit.stats.sync;

import java.util.List;

/** The kit's connected sources plus which platforms CAN be connected here. */
public record SyncStatusResponse(
        List<String> availablePlatforms,
        boolean autoSync,
        List<SyncSourceResponse> sources) {
}
