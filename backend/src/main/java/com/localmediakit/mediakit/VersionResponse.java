package com.localmediakit.mediakit;

public record VersionResponse(
        int version,
        String slug,
        String publishedAt,
        boolean active) {

    public static VersionResponse from(MediaKitVersion version, Long activeVersionId) {
        return new VersionResponse(
                version.getVersionNumber(),
                version.getSlug(),
                version.getPublishedAt().toString(),
                version.getId().equals(activeVersionId));
    }
}
