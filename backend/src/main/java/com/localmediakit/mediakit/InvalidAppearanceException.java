package com.localmediakit.mediakit;

/** An appearance value outside the curated set. */
public class InvalidAppearanceException extends RuntimeException {

    public InvalidAppearanceException(String message) {
        super(message);
    }
}
