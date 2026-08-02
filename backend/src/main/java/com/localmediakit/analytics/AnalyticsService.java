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
import java.util.Locale;
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
    private final PageViewDailyRepository dailyRepository;
    private final MediaKitVersionRepository versionRepository;
    private final MediaKitAccess access;
    private final VisitorFingerprint fingerprint;
    private final PlanPolicy planPolicy;
    private final ShareLinkService shareLinkService;

    public AnalyticsService(PageViewRepository pageViewRepository,
                            PageViewDailyRepository dailyRepository,
                            MediaKitVersionRepository versionRepository,
                            MediaKitAccess access,
                            VisitorFingerprint fingerprint,
                            PlanPolicy planPolicy,
                            ShareLinkService shareLinkService) {
        this.pageViewRepository = pageViewRepository;
        this.dailyRepository = dailyRepository;
        this.versionRepository = versionRepository;
        this.access = access;
        this.fingerprint = fingerprint;
        this.planPolicy = planPolicy;
        this.shareLinkService = shareLinkService;
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
        PageView view = new PageView(
                kitId, request.slug(), visitor,
                referrerHost(request.referrer()), UserAgents.device(userAgent));
        // Resolved against this kit only, so a token from someone else's kit
        // cannot move a view into their numbers.
        view.attributeTo(shareLinkService.resolveForKit(request.shareToken(), kitId));
        pageViewRepository.save(view);
    }

    /** Owner-facing aggregates; detail level depends on the owner's plan. */
    @Transactional(readOnly = true)
    public AnalyticsResponse analyticsFor(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        User owner = access.requireUser(userEmail);
        // Lifetime totals span both halves of the data: the days that have been
        // folded into the rollup, and the raw rows still inside the retention
        // window. Reading only the raw table -- which is what this did before
        // retention existed -- would make a creator's lifetime count shrink
        // every time the job ran.
        long totalViews = dailyRepository.sumViews(kit.getId())
                + pageViewRepository.countByMediaKitId(kit.getId());

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
        // These two are read from raw rows only, so they cover the retention
        // window rather than all time. Deliberate: keeping every visit forever
        // to make a two-year-old referrer exact is the wrong trade, and nobody
        // acts on one. Documented in the README rather than left to be noticed.
        List<AnalyticsResponse.CountEntry> referrers = toEntries(
                pageViewRepository.topReferrers(kit.getId()), "(dogrudan)");
        List<AnalyticsResponse.CountEntry> devices = toEntries(
                pageViewRepository.deviceBreakdown(kit.getId()), "UNKNOWN");

        // Summing daily distinct counts is exact here, not an approximation:
        // visitor_hash includes the day, so a returning visitor is already a
        // different hash tomorrow and the all-time distinct count was always
        // the sum of the daily ones.
        long uniqueVisitors = dailyRepository.sumUniqueVisitors(kit.getId())
                + pageViewRepository.countUniqueVisitors(kit.getId());

        return new AnalyticsResponse(
                owner.getPlan().name(), totalViews, uniqueVisitors,
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
            // Locale.ROOT: hostnames are ASCII, but the default-locale overload
            // maps "I" to a dotless "ı" on a Turkish JVM — the same referrer
            // would then land in two different buckets depending on the server.
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            log.debug("Unparseable referrer dropped: {}", referrer);
            return null;
        }
    }
}
