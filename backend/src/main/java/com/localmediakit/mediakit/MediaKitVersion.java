package com.localmediakit.mediakit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Immutable publish snapshot. Once written it is never modified: re-publishing
 * creates a new row, and rolling back re-points the kit's published version.
 * Hence no mutators below.
 */
@Entity
@Table(name = "media_kit_versions")
public class MediaKitVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** Slug at publish time (also inside content_json); denormalized for lookups. */
    @Column(nullable = false)
    private String slug;

    @Column(name = "content_json", columnDefinition = "text", nullable = false)
    private String contentJson;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected MediaKitVersion() {
        // for JPA
    }

    public MediaKitVersion(Long mediaKitId, int versionNumber, String slug, String contentJson) {
        this.mediaKitId = mediaKitId;
        this.versionNumber = versionNumber;
        this.slug = slug;
        this.contentJson = contentJson;
        this.publishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getSlug() {
        return slug;
    }

    public String getContentJson() {
        return contentJson;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
