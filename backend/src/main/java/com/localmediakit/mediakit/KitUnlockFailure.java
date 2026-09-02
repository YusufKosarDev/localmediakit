package com.localmediakit.mediakit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One wrong password on a protected kit, from one client, at one moment. */
@Entity
@Table(name = "kit_unlock_failures")
public class KitUnlockFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Slug plus client IP. Not a fingerprint and not stored anywhere else:
     * these rows exist to be counted for fifteen minutes and then deleted.
     */
    @Column(name = "attempt_key", nullable = false, length = 255)
    private String attemptKey;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    protected KitUnlockFailure() {
        // for JPA
    }

    public KitUnlockFailure(String attemptKey, Instant failedAt) {
        this.attemptKey = attemptKey;
        this.failedAt = failedAt;
    }

    public Long getId() {
        return id;
    }

    public String getAttemptKey() {
        return attemptKey;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
