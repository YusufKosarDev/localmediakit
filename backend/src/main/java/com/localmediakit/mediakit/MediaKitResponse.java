package com.localmediakit.mediakit;

public record MediaKitResponse(
        Long id,
        String slug,
        String title,
        String headline,
        String avatarUrl,
        String theme,
        String accent,
        String layout,
        String language,
        String status,
        String publishedSlug,
        boolean passwordProtected,
        boolean contactEnabled,
        String createdAt,
        String updatedAt,
        /** When this kit is due to publish itself; null when nothing is armed. */
        String scheduledPublishAt,
        /** Why the last scheduled attempt did not go out. */
        String scheduleError) {

    public static MediaKitResponse from(MediaKit kit, String publishedSlug, boolean passwordProtected) {
        return new MediaKitResponse(
                kit.getId(),
                kit.getSlug(),
                kit.getTitle(),
                kit.getHeadline(),
                kit.getAvatarUrl(),
                kit.getTheme(),
                kit.getAccent(),
                kit.getLayout(),
                kit.getLanguage(),
                kit.getStatus().name(),
                publishedSlug,
                passwordProtected,
                kit.isContactEnabled(),
                kit.getCreatedAt().toString(),
                kit.getUpdatedAt().toString(),
                kit.getScheduledPublishAt() == null ? null : kit.getScheduledPublishAt().toString(),
                kit.getScheduleError());
    }
}
