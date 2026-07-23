package com.localmediakit.mediakit;

/**
 * A minted preview link. Only the token travels; the frontend builds the URL
 * from its own origin, so the backend needs no knowledge of where it is hosted.
 */
public record PreviewLinkResponse(String token, String expiresAt) {
}
