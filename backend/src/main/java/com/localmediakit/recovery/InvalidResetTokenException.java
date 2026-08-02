package com.localmediakit.recovery;

/**
 * One exception for unknown, expired and already-used. Distinguishing them
 * would tell someone guessing which of their guesses was closer.
 */
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("This reset link is invalid or has expired. Please request a new one.");
    }
}
