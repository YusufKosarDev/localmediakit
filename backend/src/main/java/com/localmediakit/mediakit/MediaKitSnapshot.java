package com.localmediakit.mediakit;

/**
 * What gets frozen into media_kit_versions.content_json at publish time.
 * The public page renders exclusively from this — never from the live draft.
 */
public record MediaKitSnapshot(
        String slug,
        String title,
        String headline,
        String avatarUrl,
        String theme,
        String displayName) {

    public static MediaKitSnapshot of(MediaKit kit, String displayName) {
        return new MediaKitSnapshot(
                kit.getSlug(),
                kit.getTitle(),
                kit.getHeadline(),
                kit.getAvatarUrl(),
                kit.getTheme(),
                displayName);
    }
}
