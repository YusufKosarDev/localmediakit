package com.localmediakit.ratecard;

import java.math.BigDecimal;

public record RateCardResponse(
        Long id,
        String serviceName,
        BigDecimal priceAmount,
        String currency,
        String note,
        int displayOrder) {

    public static RateCardResponse from(RateCardItem item) {
        return new RateCardResponse(
                item.getId(),
                item.getServiceName(),
                item.getPriceAmount(),
                item.getCurrency(),
                item.getNote(),
                item.getDisplayOrder());
    }
}
