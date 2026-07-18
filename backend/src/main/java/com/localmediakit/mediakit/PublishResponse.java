package com.localmediakit.mediakit;

public record PublishResponse(
        Long kitId,
        int version,
        String slug,
        String publishedAt,
        int revalidateStatus) {
}
