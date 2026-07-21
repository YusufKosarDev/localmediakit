package com.localmediakit.mediakit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
        @NotBlank @Size(min = 6, max = 72) String password) {
}
