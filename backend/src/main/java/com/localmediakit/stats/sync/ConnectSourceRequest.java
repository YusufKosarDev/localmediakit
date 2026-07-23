package com.localmediakit.stats.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConnectSourceRequest(
        @NotBlank @Size(max = 255) String externalId) {
}
