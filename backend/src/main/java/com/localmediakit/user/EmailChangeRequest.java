package com.localmediakit.user;

import com.localmediakit.notification.DueAt;
import com.localmediakit.notification.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * A pending move of an account to another address, and the mail that tells the
 * owner it is pending.
 *
 * <p>Both in one row, which is a departure from the password-reset flow next
 * door. There the token and its outbox row are separate because the outbox must
 * not carry the plaintext, and because a reset has to answer identically for an
 * address that has an account and one that does not. Neither applies here: this
 * row stores a hash and never a secret, and the request is authenticated — the
 * caller proved the account's password one line before it was written.
 */
@Entity
@Table(name = "email_change_requests")
public class EmailChangeRequest {

    /** 1, 5, then 25 minutes, matching the other two outboxes. */
    static final int MAX_ATTEMPTS = 4;

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Where the account is going, once somebody proves they can read mail there. */
    @Column(name = "new_email", nullable = false)
    private String newEmail;

    /** sha256 of the token in the link; rotated on every delivery attempt. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(nullable = false, length = 10)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailChangeRequest() {
        // for JPA
    }

    public EmailChangeRequest(Long userId, String newEmail, String tokenHash,
                              Duration ttl, String locale) {
        this.userId = userId;
        this.newEmail = newEmail;
        this.tokenHash = tokenHash;
        this.locale = locale;
        this.createdAt = DueAt.now();
        this.expiresAt = this.createdAt.plus(ttl);
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        // Due immediately: somebody is watching an inbox for this.
        this.nextAttemptAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getLocale() {
        return locale;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public boolean isRedeemable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Replaces the secret and restarts the clock, at the moment a mail is about
     * to carry it.
     *
     * <p>The alternative — minting once when the change was requested — means
     * the last retry, half an hour into the backoff, delivers a link that
     * expired before it arrived. That is worse than sending nothing, because to
     * the person waiting it looks like the product is broken.
     */
    public void rotate(String freshTokenHash, Duration ttl) {
        this.tokenHash = freshTokenHash;
        this.expiresAt = Instant.now().plus(ttl);
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    /** Superseded by a newer request, or the account went away. */
    public void markObsolete() {
        this.status = NotificationStatus.SUPPRESSED;
        this.usedAt = Instant.now();
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.lastError = null;
        this.attempts++;
    }

    public void markAttemptFailed(String error) {
        this.attempts++;
        this.lastError = truncate(error);
        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = NotificationStatus.FAILED;
            return;
        }
        this.status = NotificationStatus.PENDING;
        this.nextAttemptAt = Instant.now().plus(backoffFor(this.attempts));
    }

    static Duration backoffFor(int attempts) {
        return Duration.ofMinutes((long) Math.pow(5, attempts - 1));
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
