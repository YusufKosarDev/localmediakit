package com.localmediakit.ratecard;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RateCardRequest(
        @NotBlank @Size(max = 255) String serviceName,
        @NotNull @DecimalMin("0") BigDecimal priceAmount,
        @Pattern(regexp = "^(TRY|USD|EUR)?$", message = "currency must be TRY, USD or EUR") String currency,
        @Size(max = 500) String note,
        @Min(0) Integer displayOrder) {

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "TRY" : currency;
    }

    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
