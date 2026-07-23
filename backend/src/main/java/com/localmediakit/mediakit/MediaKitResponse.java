package com.localmediakit.mediakit;

public record MediaKitResponse(
        Long id,
        String slug,
        String title,
        String headline,
        String avatarUrl,
        String theme,
        String status,
        String publishedSlug,
        boolean passwordProtected,
        boolean contactEnabled,
        String createdAt,
        String updatedAt) {

    public static MediaKitResponse from(MediaKit kit, String publishedSlug, boolean passwordProtected) {
        return new MediaKitResponse(
                kit.getId(),
                kit.getSlug(),
                kit.getTitle(),
                kit.getHeadline(),
                kit.getAvatarUrl(),
                kit.getTheme(),
                kit.getStatus().name(),
                publishedSlug,
                passwordProtected,
                kit.isContactEnabled(),
                kit.getCreatedAt().toString(),
                kit.getUpdatedAt().toString());
    }
}
