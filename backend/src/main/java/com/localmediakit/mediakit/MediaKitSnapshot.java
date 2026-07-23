package com.localmediakit.mediakit;

import java.math.BigDecimal;
import java.util.List;

/**
 * What gets frozen into media_kit_versions.content_json at publish time.
 * The public page renders exclusively from this — never from the live draft,
 * never from live stats. Stats and demographics are copied in AT PUBLISH TIME
 * (values, engagement rate and growth included), so later stat entries do not
 * change a published page until the owner publishes again.
 *
 * The nested records deliberately mirror (rather than reuse) the live-side
 * types: the snapshot format must stay stable even if the live model evolves.
 */
public record MediaKitSnapshot(
        String slug,
        String title,
        String headline,
        String avatarUrl,
        String theme,
        String displayName,
        List<PlatformStatSnapshot> platforms,
        List<DemographicSnapshot> demographics,
        List<CollaborationSnapshot> collaborations,
        /** Frozen at publish: FREE publishes carry the LocalMediaKit badge, PRO ones do not. */
        Boolean showBadge,
        /** Array order IS the display order (frozen from display_order at publish). */
        List<RateCardSnapshot> rateCard,
        /** Frozen at publish: whether the public page renders the contact form. */
        Boolean contactEnabled) {

    public record PlatformStatSnapshot(
            String platform,
            long followers,
            Long avgViews,
            Long avgLikes,
            Long avgComments,
            BigDecimal engagementRate,
            BigDecimal followerGrowth30d) {
    }

    public record DemographicSnapshot(
            String category,
            String label,
            BigDecimal percentage) {
    }

    /** Array order IS the showcase order (frozen from display_order at publish). */
    public record CollaborationSnapshot(
            String brandName,
            String campaign,
            String period,
            String resultNote,
            String logoUrl) {
    }

    public record RateCardSnapshot(
            String serviceName,
            BigDecimal priceAmount,
            String currency,
            String note) {
    }

    /** Older snapshots predate stats; normalize their absent lists to empty. */
    public List<PlatformStatSnapshot> platformsOrEmpty() {
        return platforms == null ? List.of() : platforms;
    }

    public List<DemographicSnapshot> demographicsOrEmpty() {
        return demographics == null ? List.of() : demographics;
    }

    public List<CollaborationSnapshot> collaborationsOrEmpty() {
        return collaborations == null ? List.of() : collaborations;
    }

    /** Snapshots published before the badge existed default to showing it. */
    public boolean showBadgeOrDefault() {
        return showBadge == null || showBadge;
    }

    public List<RateCardSnapshot> rateCardOrEmpty() {
        return rateCard == null ? List.of() : rateCard;
    }

    /** Snapshots published before the contact form existed never rendered it. */
    public boolean contactEnabledOrDefault() {
        return contactEnabled != null && contactEnabled;
    }
}
