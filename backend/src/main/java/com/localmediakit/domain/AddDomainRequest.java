package com.localmediakit.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddDomainRequest(
        @NotBlank @Size(max = 255) String domain) {
}
