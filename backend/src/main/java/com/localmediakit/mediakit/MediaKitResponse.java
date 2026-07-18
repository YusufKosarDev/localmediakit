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
        String createdAt,
        String updatedAt) {

    public static MediaKitResponse from(MediaKit kit, String publishedSlug) {
        return new MediaKitResponse(
                kit.getId(),
                kit.getSlug(),
                kit.getTitle(),
                kit.getHeadline(),
                kit.getAvatarUrl(),
                kit.getTheme(),
                kit.getStatus().name(),
                publishedSlug,
                kit.getCreatedAt().toString(),
                kit.getUpdatedAt().toString());
    }
}
