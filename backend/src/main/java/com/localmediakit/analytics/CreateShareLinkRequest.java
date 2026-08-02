package com.localmediakit.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The label is required on purpose. An unnamed share link is a link whose whole
 * reason for existing -- telling the creator who read the kit -- has been left
 * blank, and a list of them would be indistinguishable rows.
 */
public record CreateShareLinkRequest(
        @NotBlank @Size(max = 120) String label) {
}
