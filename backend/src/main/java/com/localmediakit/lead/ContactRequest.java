package com.localmediakit.lead;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public contact-form payload. {@code website} is a honeypot: the visible form
 * never fills it (the input is hidden), so any non-empty value marks the
 * submission as bot traffic and it is silently dropped.
 */
public record ContactRequest(
        @NotBlank @Size(max = 100) String brandName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 2000) String message,
        @Size(max = 255) String website) {
}
