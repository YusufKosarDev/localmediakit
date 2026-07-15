package com.localmediakit.mediakit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMediaKitRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 500) String headline,
        @Size(max = 1000) String avatarUrl,
        @Size(max = 50) String theme,
        @Size(max = 255) String slug) {
}
