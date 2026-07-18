package com.localmediakit.mediakit;

import java.util.List;

/** Public page payload: the frozen snapshot plus version metadata. */
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
                snapshot.platformsOrEmpty(),
                snapshot.demographicsOrEmpty(),
                snapshot.collaborationsOrEmpty(),
                version.getVersionNumber(),
                version.getPublishedAt().toString());
    }
}
