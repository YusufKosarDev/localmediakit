package com.localmediakit.shared;

/** A locale outside the supported set. */
public class UnsupportedLocaleException extends RuntimeException {

    public UnsupportedLocaleException(String message) {
        super(message);
    }
}
