package com.localmediakit.mediakit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "media_kits")
public class MediaKit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    private String headline;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false)
    private String theme;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaKitStatus status;

    /** Points at the ACTIVE snapshot in media_kit_versions; null until first publish. */
    @Column(name = "published_version_id")
    private Long publishedVersionId;

    /** BCrypt hash of the draft's access password; null = public kit. */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Contact-form ingestion switch. Disabling stops lead ingestion IMMEDIATELY
     * (kill switch); the form itself leaves the public page on the next publish
     * (frozen-snapshot rule).
     */
    @Column(name = "contact_enabled", nullable = false)
    private boolean contactEnabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaKit() {
        // for JPA
    }

    public MediaKit(Long userId, String slug, String title, String headline, String avatarUrl, String theme) {
        this.userId = userId;
        this.slug = slug;
        this.title = title;
        this.headline = headline;
        this.avatarUrl = avatarUrl;
        this.theme = (theme == null || theme.isBlank()) ? "light" : theme;
        this.status = MediaKitStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(String title, String headline, String avatarUrl, String theme) {
        this.title = title;
        this.headline = headline;
        this.avatarUrl = avatarUrl;
        this.theme = (theme == null || theme.isBlank()) ? "light" : theme;
        this.updatedAt = Instant.now();
    }

    public void changeSlug(String slug) {
        this.slug = slug;
        this.updatedAt = Instant.now();
    }

    public void setContactEnabled(boolean contactEnabled) {
        this.contactEnabled = contactEnabled;
        this.updatedAt = Instant.now();
    }

    /** Sets or clears the draft access password (pass null to make it public). */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    /**
     * State machine: DRAFT -> PUBLISHED on first publish; a PUBLISHED kit stays
     * PUBLISHED and only its active version pointer moves (re-publish appends a
     * new version, rollback re-points to an old one).
     */
    /** Detaches the live pointer so the row (and its versions) can be deleted. */
    public void clearPublishedVersion() {
        this.publishedVersionId = null;
    }

    public void activateVersion(Long versionId) {
        this.publishedVersionId = versionId;
        this.status = MediaKitStatus.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getHeadline() {
        return headline;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getTheme() {
        return theme;
    }

    public MediaKitStatus getStatus() {
        return status;
    }

    public Long getPublishedVersionId() {
        return publishedVersionId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isPasswordProtected() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public boolean isContactEnabled() {
        return contactEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
