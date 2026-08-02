package com.localmediakit.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One day of a kit's traffic, folded into a single row so the raw views behind
 * it can be deleted without the totals moving.
 *
 * <p>Unlike {@link PageView} this row is updated: a day can be rolled up more
 * than once if the retention job runs while views for it are still arriving, so
 * counts are added to what is already there rather than replacing it.
 */
@Entity
@Table(name = "page_view_daily")
public class PageViewDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Column(name = "view_day", nullable = false)
    private LocalDate viewDay;

    @Column(nullable = false)
    private long views;

    /**
     * Exact rather than approximate. The fingerprint that produces
     * visitor_hash includes the day, so it rotates at midnight: the same person
     * is a different hash tomorrow, and the all-time distinct count was already
     * the sum of the daily ones.
     */
    @Column(name = "unique_visitors", nullable = false)
    private long uniqueVisitors;

    protected PageViewDaily() {
        // for JPA
    }

    public PageViewDaily(Long mediaKitId, LocalDate viewDay, long views, long uniqueVisitors) {
        this.mediaKitId = mediaKitId;
        this.viewDay = viewDay;
        this.views = views;
        this.uniqueVisitors = uniqueVisitors;
    }

    /** Folds a second pass over the same day into this row. */
    public void add(long moreViews, long moreUniqueVisitors) {
        this.views += moreViews;
        this.uniqueVisitors += moreUniqueVisitors;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public LocalDate getViewDay() {
        return viewDay;
    }

    public long getViews() {
        return views;
    }

    public long getUniqueVisitors() {
        return uniqueVisitors;
    }
}
