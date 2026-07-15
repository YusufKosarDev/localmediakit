package com.localmediakit.mediakit;

public class MediaKitNotFoundException extends RuntimeException {
    public MediaKitNotFoundException() {
        super("Media kit not found");
    }
}
