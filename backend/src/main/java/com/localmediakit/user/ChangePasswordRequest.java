package com.localmediakit.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The current password is mandatory: a session that was left open (or stolen)
 * must not be enough to lock the real owner out of their own account.
 *
 * <p>The new password uses the same 8..72 rule as registration — 72 is BCrypt's
 * significant-byte limit, so anything longer would be silently truncated.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
