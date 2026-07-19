package com.localmediakit.mediakit;

public class TooManyUnlockAttemptsException extends RuntimeException {
    public TooManyUnlockAttemptsException() {
        super("Too many attempts. Please wait a few minutes and try again.");
    }
}
