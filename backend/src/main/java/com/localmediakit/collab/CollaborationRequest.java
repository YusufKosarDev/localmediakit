package com.localmediakit.collab;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollaborationRequest(
        @NotBlank @Size(max = 255) String brandName,
        @Size(max = 500) String campaign,
        @Size(max = 100) String period,
        @Size(max = 1000) String resultNote,
        @Size(max = 1000) String logoUrl,
        @Min(0) Integer displayOrder) {

    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
