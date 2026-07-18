package com.localmediakit.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrackRequest(
        @NotBlank @Size(max = 255) String slug,
        @Size(max = 1000) String referrer) {
}
