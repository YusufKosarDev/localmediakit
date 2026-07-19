package com.localmediakit.mediakit;

import java.util.List;

/**
 * Public page payload. Two shapes share one type:
 *  - full: a public kit's complete content (protected = false);
 *  - locked: a protected kit's minimal metadata (protected = true, sensitive
 *    fields null) — the sensitive content is served only by the unlock endpoint,
 *    so it never reaches the edge cache.
 */
public record PublicKitResponse(
        String slug,
        String title,
        String headline,
        String avatarUrl,
        String theme,
        String displayName,
        List<MediaKitSnapshot.PlatformStatSnapshot> platforms,
        List<MediaKitSnapshot.DemographicSnapshot> demographics,
        List<MediaKitSnapshot.CollaborationSnapshot> collaborations,
        boolean showBadge,
        boolean isProtected,
        int version,
        String publishedAt) {

    /** Full content — for public kits and for the authenticated unlock response. */
    public static PublicKitResponse full(MediaKitSnapshot snapshot, MediaKitVersion version) {
        return new PublicKitResponse(
                snapshot.slug(),
                snapshot.title(),
                snapshot.headline(),
                snapshot.avatarUrl(),
                snapshot.theme(),
                snapshot.displayName(),
                snapshot.platformsOrEmpty(),
                snapshot.demographicsOrEmpty(),
                snapshot.collaborationsOrEmpty(),
                snapshot.showBadgeOrDefault(),
                false,
                version.getVersionNumber(),
                version.getPublishedAt().toString());
    }

    /** Locked gate — only what a password prompt needs; no sensitive data. */
    public static PublicKitResponse locked(MediaKitSnapshot snapshot, MediaKitVersion version) {
        return new PublicKitResponse(
                snapshot.slug(),
                snapshot.title(),
                null, null, snapshot.theme(), null,
                null, null, null,
                snapshot.showBadgeOrDefault(),
                true,
                version.getVersionNumber(),
                version.getPublishedAt().toString());
    }
}
