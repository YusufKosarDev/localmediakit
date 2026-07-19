package com.localmediakit.mediakit;

public class InvalidKitPasswordException extends RuntimeException {
    public InvalidKitPasswordException() {
        super("Incorrect password");
    }
}
