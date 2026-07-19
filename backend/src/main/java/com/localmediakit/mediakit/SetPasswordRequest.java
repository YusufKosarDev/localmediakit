package com.localmediakit.mediakit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
        @NotBlank @Size(min = 4, max = 100) String password) {
}
