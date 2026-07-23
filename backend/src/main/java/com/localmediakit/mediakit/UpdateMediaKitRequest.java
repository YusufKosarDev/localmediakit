package com.localmediakit.mediakit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMediaKitRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 500) String headline,
        @Size(max = 1000) @Pattern(regexp = "^(https://.+)?$", message = "avatarUrl must start with https://") String avatarUrl,
        @Size(max = 50) String theme,
        @Size(max = 255) String slug,
        /** null = leave the contact-form switch unchanged. */
        Boolean contactEnabled) {
}
