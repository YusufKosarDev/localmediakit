package com.localmediakit.media;

public record MediaResponse(
        Long id,
        String title,
        String url,
        String thumbnailUrl,
        String platform,
        String note,
        int displayOrder) {

    public static MediaResponse from(MediaItem item) {
        return new MediaResponse(
                item.getId(), item.getTitle(), item.getUrl(), item.getThumbnailUrl(),
                item.getPlatform(), item.getNote(), item.getDisplayOrder());
    }
}
