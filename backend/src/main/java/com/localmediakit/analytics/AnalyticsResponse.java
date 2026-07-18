package com.localmediakit.analytics;

import java.util.List;

/**
 * Plan-aware analytics payload. FREE gets the total only (detailed fields
 * null); PRO gets the full breakdown. Step 7 flips plans via billing.
 */
public record AnalyticsResponse(
        String plan,
        long totalViews,
        Long uniqueVisitors,
        List<DailyViews> viewsByDay,
        List<CountEntry> referrers,
        List<CountEntry> devices) {

    public static AnalyticsResponse freeTier(String plan, long totalViews) {
        return new AnalyticsResponse(plan, totalViews, null, null, null, null);
    }

    public record DailyViews(String date, long views, long uniqueVisitors) {
    }

    public record CountEntry(String label, long count) {
    }
}
