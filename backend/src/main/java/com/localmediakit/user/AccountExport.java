package com.localmediakit.user;

import java.util.List;

/**
 * Everything an account owns, as one document.
 *
 * <p><b>The line this draws.</b> The export contains the creator's data, not
 * their visitors'. Page views and the anonymous fingerprints behind them are
 * records about other people who happened to open a page; handing a creator a
 * file full of pseudonymous visitor identifiers would be exporting somebody
 * else's data under the heading of exporting their own. Visit counts are here
 * because they are facts about the creator's page. The rows behind them are not.
 *
 * <p>The same reasoning removes the visitor fingerprint from every lead. What a
 * brand wrote and the address they gave is correspondence addressed to the
 * creator and is theirs; the hash that deduplicated the submission is not.
 *
 * <p>Nothing derived from the password appears at all -- not the hash, not its
 * length. A data export is a file that ends up in downloads folders and email
 * attachments, and a password hash is the one thing in this schema whose whole
 * value is that it never leaves the database.
 */
public record AccountExport(
        String exportedAt,
        Profile profile,
        List<Kit> kits) {

    public record Profile(
            String email,
            String displayName,
            String avatarUrl,
            String theme,
            String locale,
            boolean leadNotificationsEnabled,
            String createdAt) {
    }

    public record Kit(
            String slug,
            String title,
            String headline,
            String avatarUrl,
            String theme,
            String accent,
            String layout,
            String language,
            String status,
            boolean passwordProtected,
            boolean contactEnabled,
            String createdAt,
            /** Current values, not the whole series: a series is an export of its own size. */
            List<Stat> stats,
            List<Demographic> demographics,
            List<Collaboration> collaborations,
            List<RateCardEntry> rateCard,
            List<MediaEntry> media,
            List<Lead> leads,
            List<ShareLink> shareLinks,
            List<PublishedVersion> publishedVersions,
            Analytics analytics) {
    }

    public record Stat(String platform, long followers, Long avgViews, Long avgLikes,
                       Long avgComments, String recordedAt) {
    }

    public record Demographic(String category, String label, String percentage) {
    }

    public record Collaboration(String brandName, String campaign, String period,
                                String resultNote, String logoUrl) {
    }

    public record RateCardEntry(String serviceName, String priceAmount, String currency, String note) {
    }

    public record MediaEntry(String title, String url, String thumbnailUrl,
                             String platform, String note) {
    }

    /** No visitor fingerprint: the message is the creator's, the identifier is not. */
    public record Lead(String brandName, String email, String message,
                       String status, String createdAt) {
    }

    public record ShareLink(String label, boolean active, long views, String createdAt) {
    }

    public record PublishedVersion(int version, String slug, String publishedAt, boolean active) {
    }

    /** Counts only. The rows behind them are about visitors, not about this account. */
    public record Analytics(long totalViews, long uniqueVisitors) {
    }
}
