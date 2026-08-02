package com.localmediakit.mediakit;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * An instant, not a local date and time. The creator picks a moment in their
 * own timezone and the browser converts it; carrying a wall-clock string would
 * mean the server having to guess which clock it belonged to.
 */
public record ScheduleRequest(@NotNull Instant publishAt) {
}
