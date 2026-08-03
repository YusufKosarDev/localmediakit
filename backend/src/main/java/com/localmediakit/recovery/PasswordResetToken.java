package com.localmediakit.recovery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/** A single-use, short-lived permission to choose a new password. */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** sha256 of the token that was mailed; the token itself is never stored. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PasswordResetToken() {
        // for JPA
    }

    public PasswordResetToken(Long userId, String tokenHash, Duration ttl) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plus(ttl);
    }

    /**
     * Usable exactly once, and only inside its window.
     *
     * <p>Checked here rather than in the query so the rule lives in one place;
     * the endpoint and any future caller cannot disagree about what "valid"
     * means.
     */
    public boolean isRedeemable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    /**
     * Replaces the secret behind this permission and restarts its clock.
     *
     * <p>Called once per delivery attempt. The alternative — mailing the token
     * minted when the request came in — would need that plaintext to survive in
     * the outbox, which is the one thing {@code token_hash} exists to prevent,
     * and would post a link whose 30 minutes had been running since long before
     * it was sent. A retry 25 minutes late would arrive already dead.
     *
     * <p>Rotating in place rather than inserting a replacement keeps it at one
     * row per request, so retries cannot inflate the per-account hourly cap and
     * lock somebody out of asking again while mail is failing them.
     */
    public void rotate(String newTokenHash, Duration ttl) {
        this.tokenHash = newTokenHash;
        this.expiresAt = Instant.now().plus(ttl);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
