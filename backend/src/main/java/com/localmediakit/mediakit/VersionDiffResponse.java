package com.localmediakit.mediakit;

import java.util.List;

/**
 * Structured comparison of two immutable snapshots. Everything is expressed as
 * display-ready from/to strings: the frontend renders, it does not compute.
 */
public record VersionDiffResponse(
        int fromVersion,
        int toVersion,
        List<FieldChange> fields,
        List<PlatformDiff> platforms,
        ListDiff collaborations,
        ListDiff rateCard,
        ListDiff demographics) {

    /** A changed scalar kit field (title, headline, theme, ...). */
    public record FieldChange(String field, String from, String to) {
    }

    /** One changed metric within a platform or list entry. */
    public record MetricChange(String metric, String from, String to) {
    }

    /** kind: ADDED (absent before), REMOVED (absent after) or CHANGED. */
    public record PlatformDiff(String platform, String kind, List<MetricChange> changes) {
    }

    /**
     * Entries added/removed by display name; in-place edits as metric rows.
     * No is/get-prefixed helpers here on purpose: Jackson would serialize them
     * into the payload (an earlier isEmpty() leaked an "empty" field).
     */
    public record ListDiff(List<String> added, List<String> removed, List<MetricChange> changed) {
    }
}
