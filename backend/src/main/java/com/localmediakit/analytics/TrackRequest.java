package com.localmediakit.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrackRequest(
        @NotBlank @Size(max = 255) String slug,
        @Size(max = 1000) String referrer,
        /**
         * The share link this visit came through, if the URL carried one.
         * Optional and never trusted: an unknown or foreign token leaves the
         * view counted and unattributed rather than rejected.
         */
        @Size(max = 32) String shareToken) {
}
