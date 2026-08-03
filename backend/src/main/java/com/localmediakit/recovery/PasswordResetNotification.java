package com.localmediakit.recovery;

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
 * One queued "choose a new password" email.
 *
 * <p>Holds who to write to and which token row to rotate, and nothing that
 * could be used to log in. The token itself is never here; see the V27
 * migration for why that is the load-bearing decision rather than an omission.
 */
@Entity
@Table(name = "password_reset_notifications")
public class PasswordResetNotification {

    /**
     * Attempts before the row is written off.
     *
     * <p>The same budget the lead outbox uses, for the same reason: a mail
     * outage is usually minutes. It is not tied to the token's lifetime, and
     * does not need to be — the dispatcher mints a fresh token on every
     * attempt, so a late delivery carries a link that is valid from the moment
     * it is sent.
     */
    static final int MAX_ATTEMPTS = 4;

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The row to rotate and mail. Deleted with it, so a used-up reset cleans up after itself. */
    @Column(name = "token_id", nullable = false)
    private Long tokenId;

    /**
     * Snapshotted at enqueue time. An address change between the request and
     * the send should not redirect a recovery mail to somewhere new — that
     * would turn a mail delay into an account takeover.
     */
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

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

    protected PasswordResetNotification() {
        // for JPA
    }

    public PasswordResetNotification(Long tokenId, String recipientEmail, String locale) {
        this.tokenId = tokenId;
        this.recipientEmail = recipientEmail;
        this.locale = locale;
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
        // Due immediately: the person is waiting on this one.
        this.nextAttemptAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTokenId() {
        return tokenId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
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

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.lastError = null;
        this.attempts++;
    }

    /** The token was redeemed or invalidated before this row drained; nothing left to send. */
    public void markObsolete() {
        this.status = NotificationStatus.SUPPRESSED;
        this.attempts++;
    }

    /**
     * Records a failed delivery. Stays PENDING behind an exponential backoff
     * until the budget is gone, then becomes terminal.
     */
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

    /** 1, 5, then 25 minutes — the lead outbox's curve, and for the same blips. */
    static Duration backoffFor(int attempts) {
        return Duration.ofMinutes((long) Math.pow(5, attempts - 1));
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
