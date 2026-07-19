package com.localmediakit.billing;

public record BillingStatusResponse(
        String plan,
        String subscriptionStatus,
        String currentPeriodEnd,
        boolean stripeEnabled) {
}
