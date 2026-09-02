package com.localmediakit.user;

import jakarta.validation.constraints.NotBlank;

/** The token from the link in the verification mail, and nothing else. */
public record EmailChangeConfirmRequest(@NotBlank String token) {
}
