package com.localmediakit.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Email changes are confirmed by the current password rather than by a
 * verification link. Re-authentication is the control that actually matters
 * here — it stops an open session from silently moving the account to an
 * attacker's address, which a link sent to the new address would not.
 *
 * <p>It used to say the application had no mail sender and so could not deliver
 * a verification link. That stopped being true when password reset arrived:
 * there is a sender now, and confirming the new address is a {@code
 * pending_email} column and a token away. It is no longer blocked, only
 * unbuilt — and the README says so in the same words.
 */
public record ChangeEmailRequest(
        @NotBlank String currentPassword,
        @NotBlank @Email String newEmail) {
}
