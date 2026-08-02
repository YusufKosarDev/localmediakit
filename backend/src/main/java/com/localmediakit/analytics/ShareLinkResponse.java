package com.localmediakit.analytics;

/**
 * A share link as the dashboard shows it: what it was called, the URL to send,
 * and what came back through it.
 */
public record ShareLinkResponse(
        Long id,
        String label,
        String token,
        /** The full link, assembled here so the client never rebuilds the shape. */
        String url,
        boolean active,
        long views,
        long uniqueVisitors,
        String createdAt,
        String revokedAt) {

    static ShareLinkResponse from(KitShareLink link, String slug, long views, long uniqueVisitors) {
        return new ShareLinkResponse(
                link.getId(),
                link.getLabel(),
                link.getToken(),
                // Relative: the dashboard knows its own origin, and hard-coding
                // one here would put the wrong host in every local link.
                "/" + slug + "?r=" + link.getToken(),
                link.isActive(),
                views,
                uniqueVisitors,
                link.getCreatedAt().toString(),
                link.getRevokedAt() == null ? null : link.getRevokedAt().toString());
    }
}
