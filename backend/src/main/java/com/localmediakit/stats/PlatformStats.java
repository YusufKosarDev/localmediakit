package com.localmediakit.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One point in the append-only stats time series. Rows are never updated:
 * a new measurement is a new row, which is what makes trends computable.
 */
@Entity
@Table(name = "platform_stats")
public class PlatformStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(nullable = false)
    private long followers;

    @Column(name = "avg_views")
    private Long avgViews;

    @Column(name = "avg_likes")
    private Long avgLikes;

    @Column(name = "avg_comments")
    private Long avgComments;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected PlatformStats() {
        // for JPA
    }

    public PlatformStats(Long mediaKitId, Platform platform, long followers,
                         Long avgViews, Long avgLikes, Long avgComments) {
        this(mediaKitId, platform, followers, avgViews, avgLikes, avgComments, Instant.now());
    }

    /** Explicit recorded_at: used by imports/seeds and time-dependent tests. */
    public PlatformStats(Long mediaKitId, Platform platform, long followers,
                         Long avgViews, Long avgLikes, Long avgComments, Instant recordedAt) {
        this.mediaKitId = mediaKitId;
        this.platform = platform;
        this.followers = followers;
        this.avgViews = avgViews;
        this.avgLikes = avgLikes;
        this.avgComments = avgComments;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public Platform getPlatform() {
        return platform;
    }

    public long getFollowers() {
        return followers;
    }

    public Long getAvgViews() {
        return avgViews;
    }

    public Long getAvgLikes() {
        return avgLikes;
    }

    public Long getAvgComments() {
        return avgComments;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
