package com.localmediakit.analytics;

public class ShareLinkNotFoundException extends RuntimeException {
    public ShareLinkNotFoundException() {
        super("Share link not found.");
    }
}
