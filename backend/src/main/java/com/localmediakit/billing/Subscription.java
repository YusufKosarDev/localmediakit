package com.localmediakit.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Billing state mirrored from Stripe webhooks (test mode only). */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    @Column(nullable = false)
    private String status;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
        // for JPA
    }

    public Subscription(Long userId, String stripeCustomerId, String stripeSubscriptionId, String status) {
        this.userId = userId;
        this.stripeCustomerId = stripeCustomerId;
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateFromStripe(String stripeCustomerId, String stripeSubscriptionId,
                                 String status, Instant currentPeriodEnd) {
        if (stripeCustomerId != null) {
            this.stripeCustomerId = stripeCustomerId;
        }
        if (stripeSubscriptionId != null) {
            this.stripeSubscriptionId = stripeSubscriptionId;
        }
        this.status = status;
        if (currentPeriodEnd != null) {
            this.currentPeriodEnd = currentPeriodEnd;
        }
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }
}
