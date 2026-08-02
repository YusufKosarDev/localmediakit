package com.localmediakit.user;

import com.localmediakit.analytics.KitShareLinkRepository;
import com.localmediakit.analytics.PageViewDailyRepository;
import com.localmediakit.analytics.PageViewRepository;
import com.localmediakit.collab.BrandCollaborationRepository;
import com.localmediakit.lead.KitLeadRepository;
import com.localmediakit.media.MediaItemRepository;
import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import com.localmediakit.mediakit.MediaKitRepository;
import com.localmediakit.mediakit.MediaKitVersionRepository;
import com.localmediakit.ratecard.RateCardItemRepository;
import com.localmediakit.stats.Platform;
import com.localmediakit.stats.PlatformStatsRepository;
import com.localmediakit.stats.AudienceDemographicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Assembles the account's data into one downloadable document.
 *
 * <p>The interesting decisions are all about what is left out; see
 * {@link AccountExport} for the reasoning. In short: this is the creator's
 * data, not their visitors', and nothing derived from the password appears
 * anywhere.
 */
@Service
public class AccountExportService {

    private final MediaKitAccess access;
    private final MediaKitRepository mediaKitRepository;
    private final MediaKitVersionRepository versionRepository;
    private final PlatformStatsRepository statsRepository;
    private final AudienceDemographicRepository demographicRepository;
    private final BrandCollaborationRepository collaborationRepository;
    private final RateCardItemRepository rateCardRepository;
    private final MediaItemRepository mediaRepository;
    private final KitLeadRepository leadRepository;
    private final KitShareLinkRepository shareLinkRepository;
    private final PageViewRepository pageViewRepository;
    private final PageViewDailyRepository dailyRepository;

    public AccountExportService(MediaKitAccess access,
                                MediaKitRepository mediaKitRepository,
                                MediaKitVersionRepository versionRepository,
                                PlatformStatsRepository statsRepository,
                                AudienceDemographicRepository demographicRepository,
                                BrandCollaborationRepository collaborationRepository,
                                RateCardItemRepository rateCardRepository,
                                MediaItemRepository mediaRepository,
                                KitLeadRepository leadRepository,
                                KitShareLinkRepository shareLinkRepository,
                                PageViewRepository pageViewRepository,
                                PageViewDailyRepository dailyRepository) {
        this.access = access;
        this.mediaKitRepository = mediaKitRepository;
        this.versionRepository = versionRepository;
        this.statsRepository = statsRepository;
        this.demographicRepository = demographicRepository;
        this.collaborationRepository = collaborationRepository;
        this.rateCardRepository = rateCardRepository;
        this.mediaRepository = mediaRepository;
        this.leadRepository = leadRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.pageViewRepository = pageViewRepository;
        this.dailyRepository = dailyRepository;
    }

    @Transactional(readOnly = true)
    public AccountExport export(String userEmail) {
        User user = access.requireUser(userEmail);
        List<AccountExport.Kit> kits = mediaKitRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::exportKit)
                .toList();

        return new AccountExport(
                Instant.now().toString(),
                new AccountExport.Profile(
                        user.getEmail(), user.getDisplayName(), user.getAvatarUrl(),
                        user.getTheme().name(), user.getLocale(),
                        user.isLeadNotificationsEnabled(), user.getCreatedAt().toString()),
                kits);
    }

    private AccountExport.Kit exportKit(MediaKit kit) {
        Long id = kit.getId();
        // One query for every link's count, rather than one per link -- the
        // same N+1 shape the kit list and the share-link panel already avoid.
        Map<Long, Long> viewsByLink = pageViewRepository.countsByShareLink(id).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a));
        return new AccountExport.Kit(
                kit.getSlug(), kit.getTitle(), kit.getHeadline(), kit.getAvatarUrl(),
                kit.getTheme(), kit.getAccent(), kit.getLayout(), kit.getLanguage(),
                kit.getPublishedVersionId() == null ? "DRAFT" : "PUBLISHED",
                kit.isPasswordProtected(), kit.isContactEnabled(),
                kit.getCreatedAt().toString(),
                currentStats(id),
                demographicRepository.findByMediaKitIdOrderByCategoryAscPercentageDesc(id).stream()
                        .map(d -> new AccountExport.Demographic(
                                d.getCategory().name(), d.getLabel(), d.getPercentage().toPlainString()))
                        .toList(),
                collaborationRepository.findByMediaKitIdOrderByDisplayOrderAscIdAsc(id).stream()
                        .map(c -> new AccountExport.Collaboration(
                                c.getBrandName(), c.getCampaign(), c.getPeriod(),
                                c.getResultNote(), c.getLogoUrl()))
                        .toList(),
                rateCardRepository.findByMediaKitIdOrderByDisplayOrderAscIdAsc(id).stream()
                        .map(r -> new AccountExport.RateCardEntry(
                                r.getServiceName(), r.getPriceAmount().toPlainString(),
                                r.getCurrency(), r.getNote()))
                        .toList(),
                mediaRepository.findByMediaKitIdOrderByDisplayOrderAscIdAsc(id).stream()
                        .map(m -> new AccountExport.MediaEntry(
                                m.getTitle(), m.getUrl(), m.getThumbnailUrl(),
                                m.getPlatform(), m.getNote()))
                        .toList(),
                // No visitor hash. The message is correspondence addressed to
                // this creator; the fingerprint that deduplicated it is not.
                leadRepository.findByMediaKitIdOrderByCreatedAtDescIdDesc(id).stream()
                        .map(l -> new AccountExport.Lead(
                                l.getBrandName(), l.getEmail(), l.getMessage(),
                                l.getStatus().name(), l.getCreatedAt().toString()))
                        .toList(),
                // Labels and counts, not tokens: a token in an exported file is
                // a live link sitting in a downloads folder.
                shareLinkRepository.findByMediaKitIdOrderByCreatedAtDesc(id).stream()
                        .map(s -> new AccountExport.ShareLink(
                                s.getLabel(), s.isActive(),
                                viewsByLink.getOrDefault(s.getId(), 0L),
                                s.getCreatedAt().toString()))
                        .toList(),
                versionRepository.findByMediaKitIdOrderByVersionNumberDesc(id).stream()
                        .map(v -> new AccountExport.PublishedVersion(
                                v.getVersionNumber(), v.getSlug(), v.getPublishedAt().toString(),
                                Objects.equals(v.getId(), kit.getPublishedVersionId())))
                        .toList(),
                new AccountExport.Analytics(
                        dailyRepository.sumViews(id) + pageViewRepository.countByMediaKitId(id),
                        dailyRepository.sumUniqueVisitors(id)
                                + pageViewRepository.countUniqueVisitors(id)));
    }

    /**
     * The latest measurement per platform, not the whole series.
     *
     * <p>A kit synced daily for a year holds hundreds of rows per platform, and
     * a creator opening this file wants to see their numbers rather than
     * scroll a log of them. The series stays in the database and stays theirs;
     * this is the document, not the backup.
     */
    private List<AccountExport.Stat> currentStats(Long kitId) {
        List<AccountExport.Stat> stats = new ArrayList<>();
        for (Platform platform : statsRepository.platformsWithData(kitId)) {
            statsRepository.findFirstByMediaKitIdAndPlatformOrderByRecordedAtDescIdDesc(kitId, platform)
                    .ifPresent(s -> stats.add(new AccountExport.Stat(
                            s.getPlatform().name(), s.getFollowers(), s.getAvgViews(),
                            s.getAvgLikes(), s.getAvgComments(), s.getRecordedAt().toString())));
        }
        return stats;
    }
}
