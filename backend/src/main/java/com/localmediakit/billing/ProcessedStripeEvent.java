package com.localmediakit.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Idempotency marker. Inserted in the SAME transaction that applies a webhook
 * event's effects, so a Stripe redelivery either finds it (and is swallowed)
 * or the first attempt failed and rolled back (and the retry starts clean).
 */
@Entity
@Table(name = "processed_stripe_events")
public class ProcessedStripeEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ProcessedStripeEvent() {
        // for JPA
    }

    public ProcessedStripeEvent(String eventId) {
        this.eventId = eventId;
        this.receivedAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }
}
