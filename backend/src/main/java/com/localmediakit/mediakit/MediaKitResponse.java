package com.localmediakit.mediakit;

public record MediaKitResponse(
        Long id,
        String slug,
        String title,
        String headline,
        String avatarUrl,
        String theme,
        String status,
        String createdAt,
        String updatedAt) {

    public static MediaKitResponse from(MediaKit kit) {
        return new MediaKitResponse(
                kit.getId(),
                kit.getSlug(),
                kit.getTitle(),
                kit.getHeadline(),
                kit.getAvatarUrl(),
                kit.getTheme(),
                kit.getStatus().name(),
                kit.getCreatedAt().toString(),
                kit.getUpdatedAt().toString());
    }
}
