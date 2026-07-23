package com.localmediakit.stats.sync;

import com.localmediakit.stats.Platform;
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
 * One connected external account per platform per kit. lastSyncedAt tracks the
 * last SUCCESS (it drives the due-for-sync cadence); a failure only records
 * lastError, so broken sources keep being retried on the normal schedule.
 */
@Entity
@Table(name = "stats_sources")
public class StatsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StatsSource() {
        // for JPA
    }

    public StatsSource(Long mediaKitId, Platform platform, String externalId) {
        this.mediaKitId = mediaKitId;
        this.platform = platform;
        this.externalId = externalId;
        this.createdAt = Instant.now();
    }

    public void updateExternalId(String externalId) {
        this.externalId = externalId;
        this.lastSyncedAt = null;
        this.lastError = null;
    }

    public void recordSuccess(Instant syncedAt) {
        this.lastSyncedAt = syncedAt;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.lastError = error == null ? "unknown error"
                : (error.length() > 500 ? error.substring(0, 500) : error);
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

    public String getExternalId() {
        return externalId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
