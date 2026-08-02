package com.localmediakit.media;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Both URLs are HTTPS-only, the same rule the kit and account avatars already
 * use. One mental model for every URL a creator hands this application, and no
 * mixed-content warning on a page a brand opens.
 */
public record MediaRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 1000)
        @Pattern(regexp = "^https://.+", message = "url must start with https://") String url,
        @Size(max = 1000)
        @Pattern(regexp = "^(https://.+)?$", message = "thumbnailUrl must start with https://")
        String thumbnailUrl,
        @Size(max = 20) String platform,
        @Size(max = 500) String note,
        @Min(0) Integer displayOrder) {

    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
