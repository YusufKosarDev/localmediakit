package com.localmediakit.mediakit;

/** Public page payload: the frozen snapshot plus version metadata. */
public record PublicKitResponse(
        String slug,
        String title,
        String headline,
        String avatarUrl,
        String theme,
        String displayName,
        int version,
        String publishedAt) {

    public static PublicKitResponse from(MediaKitSnapshot snapshot, MediaKitVersion version) {
        return new PublicKitResponse(
                snapshot.slug(),
                snapshot.title(),
                snapshot.headline(),
                snapshot.avatarUrl(),
                snapshot.theme(),
                snapshot.displayName(),
                version.getVersionNumber(),
                version.getPublishedAt().toString());
    }
}
