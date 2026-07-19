package com.localmediakit.billing;

/** The demo plan switch is forbidden while real Stripe billing is active. */
public class DemoUpgradeDisabledException extends RuntimeException {
    public DemoUpgradeDisabledException() {
        super("Demo plan switch is disabled while billing is active");
    }
}
