package com.localmediakit.media;

public class MediaItemNotFoundException extends RuntimeException {
    public MediaItemNotFoundException() {
        super("Media item not found.");
    }
}
