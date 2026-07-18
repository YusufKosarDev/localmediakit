package com.localmediakit.mediakit;

public class VersionNotFoundException extends RuntimeException {
    public VersionNotFoundException() {
        super("Version not found");
    }
}
