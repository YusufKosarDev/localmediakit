package com.localmediakit.notification;

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
 * One queued "a brand contacted you" email.
 *
 * <p>Created alongside the lead it describes and delivered later by
 * {@link LeadNotificationService}. Nothing about the lead's own persistence
 * depends on this row succeeding to send.
 */
@Entity
@Table(name = "lead_notifications")
public class LeadNotification {

    /** Attempts before the row is written off. Small: mail outages are usually minutes, not days. */
    static final int MAX_ATTEMPTS = 4;

    /** Errors are truncated to the column width — a stack trace is not the point. */
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    /**
     * Snapshotted at enqueue time so the mail goes where the owner was
     * reachable when the lead arrived, not wherever they moved to since.
     */
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

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

    protected LeadNotification() {
        // for JPA
    }

    public LeadNotification(Long leadId, String recipientEmail, NotificationStatus status) {
        this.leadId = leadId;
        this.recipientEmail = recipientEmail;
        this.status = status;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getLeadId() {
        return leadId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
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

    /**
     * Records a failed delivery. Stays PENDING with an exponential backoff
     * until the attempt budget is gone, then becomes terminal — a row that
     * retried forever would keep a broken address in the queue indefinitely.
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

    /** 1, 5, then 25 minutes — long enough to ride out a provider blip. */
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
