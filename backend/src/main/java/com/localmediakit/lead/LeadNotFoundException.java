package com.localmediakit.lead;

public class LeadNotFoundException extends RuntimeException {
    public LeadNotFoundException() {
        super("Lead not found");
    }
}
