package com.localmediakit.user;

import jakarta.validation.constraints.NotBlank;

/**
 * Deletion is irreversible and takes every published page offline with it, so
 * it asks for two independent things: proof of identity (the password) and
 * proof of intent (typing the confirmation phrase). Neither alone is enough.
 */
public record DeleteAccountRequest(
        @NotBlank String currentPassword,
        @NotBlank String confirmation) {

    /** The exact phrase the UI asks the user to type. */
    public static final String REQUIRED_CONFIRMATION = "HESABIMI SIL";
}
