package com.localmediakit.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Non-sensitive profile edit. Changing these cannot take over an account, so
 * no password confirmation is required — unlike email/password/deletion.
 *
 * <p>The avatar is an HTTPS URL, the same rule (and the same column width)
 * the kit avatar already uses. An empty string clears it.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 1000)
        @Pattern(regexp = "^(https://.+)?$", message = "avatarUrl must start with https://")
        String avatarUrl,
        Theme theme,
        /** Null keeps the current setting, so an older client cannot silently
         *  switch someone's lead emails off by omitting the field. */
        Boolean leadNotificationsEnabled) {
}
