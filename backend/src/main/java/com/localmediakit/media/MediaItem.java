package com.localmediakit.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One piece of the creator's work, shown on the public page.
 *
 * <p>A link and a thumbnail rather than an upload: the host's disk does not
 * survive a deploy, and the content already lives on the platform it was
 * published to -- which is where a brand would rather watch it anyway.
 */
@Entity
@Table(name = "kit_media_items")
public class MediaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(length = 20)
    private String platform;

    @Column(length = 500)
    private String note;

    /** Owner-chosen position (ascending), like collaborations and the rate card. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MediaItem() {
        // for JPA
    }

    public MediaItem(Long mediaKitId, String title, String url, String thumbnailUrl,
                     String platform, String note, int displayOrder) {
        this.mediaKitId = mediaKitId;
        this.createdAt = Instant.now();
        update(title, url, thumbnailUrl, platform, note, displayOrder);
    }

    public void update(String title, String url, String thumbnailUrl,
                       String platform, String note, int displayOrder) {
        this.title = title;
        this.url = url;
        this.thumbnailUrl = blankToNull(thumbnailUrl);
        this.platform = blankToNull(platform);
        this.note = blankToNull(note);
        this.displayOrder = displayOrder;
    }

    /**
     * An empty field means "not set", not "set to nothing". Storing "" would
     * put an image element with no source on the public page and a badge with
     * no text next to it.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getPlatform() {
        return platform;
    }

    public String getNote() {
        return note;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
