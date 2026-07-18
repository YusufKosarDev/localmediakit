package com.localmediakit.analytics;

import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import com.localmediakit.mediakit.MediaKitVersionRepository;
import com.localmediakit.user.PlanPolicy;
import com.localmediakit.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AnalyticsService {

    /**
     * One visitor counts once per kit within this window. This both dedups
     * refresh/back-forward noise and acts as a natural per-visitor rate limit.
     */
    static final Duration SESSION_WINDOW = Duration.ofMinutes(30);

    private static final Duration DAILY_SERIES_SPAN = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final PageViewRepository pageViewRepository;
    private final MediaKitVersionRepository versionRepository;
    private final MediaKitAccess access;
    private final VisitorFingerprint fingerprint;
    private final PlanPolicy planPolicy;

    public AnalyticsService(PageViewRepository pageViewRepository,
                            MediaKitVersionRepository versionRepository,
                            MediaKitAccess access,
                            VisitorFingerprint fingerprint,
                            PlanPolicy planPolicy) {
        this.pageViewRepository = pageViewRepository;
        this.versionRepository = versionRepository;
        this.access = access;
        this.fingerprint = fingerprint;
        this.planPolicy = planPolicy;
    }

    /**
     * Best-effort ingestion of a view beacon. Deliberately silent about the
     * outcome: bots, duplicates and unknown slugs are dropped without telling
     * the caller which case it was.
     */
    @Transactional
    public void track(TrackRequest request, String ip, String userAgent) {
        if (UserAgents.isBot(userAgent)) {
            return;
        }
        // Only ACTIVE published pages are visitable, so resolve through them.
        Long kitId = versionRepository.findActiveBySlug(request.slug())
                .map(version -> version.getMediaKitId())
                .orElse(null);
        if (kitId == null) {
            return;
        }
        String visitor = fingerprint.of(ip, userAgent);
        boolean alreadyCounted = pageViewRepository
                .existsByMediaKitIdAndVisitorHashAndViewedAtAfter(
                        kitId, visitor, Instant.now().minus(SESSION_WINDOW));
        if (alreadyCounted) {
            return;
        }
        pageViewRepository.save(new PageView(
                kitId, request.slug(), visitor,
                referrerHost(request.referrer()), UserAgents.device(userAgent)));
    }

    /** Owner-facing aggregates; detail level depends on the owner's plan. */
    @Transactional(readOnly = true)
    public AnalyticsResponse analyticsFor(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        User owner = access.requireUser(userEmail);
        long totalViews = pageViewRepository.countByMediaKitId(kit.getId());

        if (!planPolicy.detailedAnalyticsEnabled(owner.getPlan())) {
            // FREE teaser: the total only.
            return AnalyticsResponse.freeTier(owner.getPlan().name(), totalViews);
        }

        List<AnalyticsResponse.DailyViews> byDay = pageViewRepository
                .dailyCounts(kit.getId(), Instant.now().minus(DAILY_SERIES_SPAN))
                .stream()
                .map(row -> new AnalyticsResponse.DailyViews(
                        ((Date) row[0]).toLocalDate().toString(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue()))
                .toList();
        List<AnalyticsResponse.CountEntry> referrers = toEntries(
                pageViewRepository.topReferrers(kit.getId()), "(dogrudan)");
        List<AnalyticsResponse.CountEntry> devices = toEntries(
                pageViewRepository.deviceBreakdown(kit.getId()), "UNKNOWN");

        return new AnalyticsResponse(
                owner.getPlan().name(), totalViews,
                pageViewRepository.countUniqueVisitors(kit.getId()),
                byDay, referrers, devices);
    }

    private List<AnalyticsResponse.CountEntry> toEntries(List<Object[]> rows, String emptyLabel) {
        return rows.stream()
                .map(row -> new AnalyticsResponse.CountEntry(
                        ((String) row[0]).isEmpty() ? emptyLabel : (String) row[0],
                        ((Number) row[1]).longValue()))
                .toList();
    }

    /** Group referrers by host only; the full URL adds noise, not signal. */
    private String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referrer.trim()).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (Exception e) {
            log.debug("Unparseable referrer dropped: {}", referrer);
            return null;
        }
    }
}
