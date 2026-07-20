package com.localmediakit.domain;

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
@Table(name = "custom_domains")
public class CustomDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Column(nullable = false, unique = true)
    private String domain;

    @Column(name = "verification_token", nullable = false)
    private String verificationToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DomainStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomDomain() {
        // for JPA
    }

    public CustomDomain(Long mediaKitId, String domain, String verificationToken) {
        this.mediaKitId = mediaKitId;
        this.domain = domain;
        this.verificationToken = verificationToken;
        this.status = DomainStatus.PENDING;
        this.attempts = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Records a successful verification. */
    public void markVerified() {
        this.status = DomainStatus.VERIFIED;
        this.lastCheckedAt = Instant.now();
        this.updatedAt = this.lastCheckedAt;
    }

    /**
     * Records a failed check attempt. Transitions to FAILED once the attempt
     * budget is exhausted; otherwise stays PENDING for the next run.
     */
    public void recordFailedAttempt(int maxAttempts) {
        this.attempts++;
        if (this.attempts >= maxAttempts) {
            this.status = DomainStatus.FAILED;
        }
        this.lastCheckedAt = Instant.now();
        this.updatedAt = this.lastCheckedAt;
    }

    /** Explicit retry of a FAILED domain: back to PENDING, attempts reset. */
    public void resetForRetry() {
        this.status = DomainStatus.PENDING;
        this.attempts = 0;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public String getDomain() {
        return domain;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public DomainStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }
}
