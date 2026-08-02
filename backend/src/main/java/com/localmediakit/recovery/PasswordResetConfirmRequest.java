package com.localmediakit.recovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The same password rules registration uses. A recovery flow that accepted a
 * weaker password than sign-up would be the easiest way to get a weak one onto
 * an account.
 */
public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
