package com.localmediakit.billing;

public class BillingNotConfiguredException extends RuntimeException {
    public BillingNotConfiguredException() {
        super("Billing is not configured in this environment");
    }
}
