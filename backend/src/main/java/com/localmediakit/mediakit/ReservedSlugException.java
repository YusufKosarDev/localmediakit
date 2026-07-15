package com.localmediakit.mediakit;

public class ReservedSlugException extends RuntimeException {
    public ReservedSlugException(String slug) {
        super("Slug '" + slug + "' is reserved and cannot be used");
    }
}
