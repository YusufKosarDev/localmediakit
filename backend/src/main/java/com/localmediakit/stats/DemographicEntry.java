package com.localmediakit.stats;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DemographicEntry(
        @NotNull DemographicCategory category,
        @NotBlank @Size(max = 50) String label,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal percentage) {

    public static DemographicEntry from(AudienceDemographic row) {
        return new DemographicEntry(row.getCategory(), row.getLabel(), row.getPercentage());
    }
}
