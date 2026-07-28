package com.localmediakit.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Email changes are confirmed by the current password rather than by a
 * verification link: the application has no mail sender, so there is nothing
 * that could deliver one. Re-authentication is the control that actually
 * matters here — it stops an open session from silently moving the account to
 * an attacker's address.
 */
public record ChangeEmailRequest(
        @NotBlank String currentPassword,
        @NotBlank @Email String newEmail) {
}
