package com.localmediakit.ratecard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One priced deliverable on the creator's rate card (e.g. "YouTube video"). */
@Entity
@Table(name = "rate_card_items")
public class RateCardItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String note;

    /** Owner-chosen position (ascending). */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected RateCardItem() {
        // for JPA
    }

    public RateCardItem(Long mediaKitId, String serviceName, BigDecimal priceAmount,
                        String currency, String note, int displayOrder) {
        this.mediaKitId = mediaKitId;
        this.serviceName = serviceName;
        this.priceAmount = priceAmount;
        this.currency = currency;
        this.note = note;
        this.displayOrder = displayOrder;
    }

    public void update(String serviceName, BigDecimal priceAmount, String currency,
                       String note, int displayOrder) {
        this.serviceName = serviceName;
        this.priceAmount = priceAmount;
        this.currency = currency;
        this.note = note;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNote() {
        return note;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
