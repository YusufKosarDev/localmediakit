package com.localmediakit.stats.sync;

public class ExternalAccountNotFoundException extends RuntimeException {
    public ExternalAccountNotFoundException(String message) {
        super(message);
    }
}
