package com.localmediakit.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One slice of a kit's audience distribution, e.g. (AGE, "18-24", 38%). */
@Entity
@Table(name = "audience_demographics")
public class AudienceDemographic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DemographicCategory category;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    protected AudienceDemographic() {
        // for JPA
    }

    public AudienceDemographic(Long mediaKitId, DemographicCategory category,
                               String label, BigDecimal percentage) {
        this.mediaKitId = mediaKitId;
        this.category = category;
        this.label = label;
        this.percentage = percentage;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public DemographicCategory getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }
}
