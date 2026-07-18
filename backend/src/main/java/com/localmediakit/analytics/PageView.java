package com.localmediakit.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One counted view of a public page. Append-only; rows are never updated. */
@Entity
@Table(name = "page_views")
public class PageView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Column(nullable = false)
    private String slug;

    /** Anonymous daily-rotating fingerprint; the raw IP is never stored. */
    @Column(name = "visitor_hash", nullable = false)
    private String visitorHash;

    private String referrer;

    private String device;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    protected PageView() {
        // for JPA
    }

    public PageView(Long mediaKitId, String slug, String visitorHash,
                    String referrer, String device) {
        this(mediaKitId, slug, visitorHash, referrer, device, Instant.now());
    }

    /** Explicit viewed_at: used by time-dependent tests. */
    public PageView(Long mediaKitId, String slug, String visitorHash,
                    String referrer, String device, Instant viewedAt) {
        this.mediaKitId = mediaKitId;
        this.slug = slug;
        this.visitorHash = visitorHash;
        this.referrer = referrer;
        this.device = device;
        this.viewedAt = viewedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public String getSlug() {
        return slug;
    }

    public String getVisitorHash() {
        return visitorHash;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getDevice() {
        return device;
    }

    public Instant getViewedAt() {
        return viewedAt;
    }
}
