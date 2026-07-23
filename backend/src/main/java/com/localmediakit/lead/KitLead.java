package com.localmediakit.lead;

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
 * A brand's collaboration request submitted from the public page. Append-only
 * from the visitor's side; only the owner mutates it (status transitions).
 */
@Entity
@Table(name = "kit_leads")
public class KitLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status;

    /** Anonymous daily-rotating fingerprint (same scheme as page_views). */
    @Column(name = "visitor_hash", nullable = false)
    private String visitorHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KitLead() {
        // for JPA
    }

    public KitLead(Long mediaKitId, String brandName, String email, String message, String visitorHash) {
        this.mediaKitId = mediaKitId;
        this.brandName = brandName;
        this.email = email;
        this.message = message;
        this.status = LeadStatus.NEW;
        this.visitorHash = visitorHash;
        this.createdAt = Instant.now();
    }

    public void changeStatus(LeadStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public String getBrandName() {
        return brandName;
    }

    public String getEmail() {
        return email;
    }

    public String getMessage() {
        return message;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public String getVisitorHash() {
        return visitorHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
