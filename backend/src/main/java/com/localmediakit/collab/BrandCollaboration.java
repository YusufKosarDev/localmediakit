package com.localmediakit.collab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A past brand deal shown on the public page as social proof. */
@Entity
@Table(name = "brand_collaborations")
public class BrandCollaboration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    private String campaign;

    private String period;

    @Column(name = "result_note")
    private String resultNote;

    @Column(name = "logo_url")
    private String logoUrl;

    /** Owner-chosen showcase position (ascending). */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected BrandCollaboration() {
        // for JPA
    }

    public BrandCollaboration(Long mediaKitId, String brandName, String campaign,
                              String period, String resultNote, String logoUrl, int displayOrder) {
        this.mediaKitId = mediaKitId;
        this.brandName = brandName;
        this.campaign = campaign;
        this.period = period;
        this.resultNote = resultNote;
        this.logoUrl = logoUrl;
        this.displayOrder = displayOrder;
    }

    public void update(String brandName, String campaign, String period,
                       String resultNote, String logoUrl, int displayOrder) {
        this.brandName = brandName;
        this.campaign = campaign;
        this.period = period;
        this.resultNote = resultNote;
        this.logoUrl = logoUrl;
        this.displayOrder = displayOrder;
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

    public String getCampaign() {
        return campaign;
    }

    public String getPeriod() {
        return period;
    }

    public String getResultNote() {
        return resultNote;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
