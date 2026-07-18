package com.localmediakit.stats;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecordStatsRequest(
        @NotNull Platform platform,
        @NotNull @Min(0) Long followers,
        @Min(0) Long avgViews,
        @Min(0) Long avgLikes,
        @Min(0) Long avgComments) {
}
