package com.localmediakit.mediakit;

public class InvalidScheduleException extends RuntimeException {
    public InvalidScheduleException() {
        super("The scheduled time must be in the future.");
    }
}
