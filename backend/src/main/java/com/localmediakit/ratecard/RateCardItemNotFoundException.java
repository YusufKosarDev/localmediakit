package com.localmediakit.ratecard;

public class RateCardItemNotFoundException extends RuntimeException {
    public RateCardItemNotFoundException() {
        super("Rate card item not found");
    }
}
