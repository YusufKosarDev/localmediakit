package com.localmediakit.billing;

public class AlreadyProException extends RuntimeException {
    public AlreadyProException() {
        super("Plan is already PRO");
    }
}
