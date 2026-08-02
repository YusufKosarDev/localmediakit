package com.localmediakit.stats.sync;

import com.localmediakit.domain.ReentrancyGuard;
import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import com.localmediakit.mediakit.MediaKitRepository;
import com.localmediakit.observability.OperationalMetrics;
import com.localmediakit.shared.ConstraintRetry;
import com.localmediakit.stats.Platform;
import com.localmediakit.stats.RecordStatsRequest;
import com.localmediakit.stats.StatsService;
import com.localmediakit.user.PlanPolicy;
import com.localmediakit.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Connect / manual-sync / scheduled-batch pipeline for external stat sources.
 * Every successful fetch APPENDS to the platform_stats series through
 * {@link StatsService}, so engagement and 30-day growth come for free.
 *
 * Plan rule: connecting and manual sync are open to everyone; the scheduled
 * batch only refreshes PRO owners' sources ({@link PlanPolicy#autoSyncEnabled}).
 */
@Service
public class StatsSyncService {

    private static final Logger log = LoggerFactory.getLogger(StatsSyncService.class);

    private final StatsSourceRepository sourceRepository;
    private final MediaKitRepository mediaKitRepository;
    private final MediaKitAccess access;
    private final StatsProviderRegistry providers;
    private final StatsService statsService;
    private final PlanPolicy planPolicy;
    private final OperationalMetrics metrics;
    private final TransactionTemplate transactionTemplate;
    // Own instance, NOT the shared domain-job bean: the two scheduled batches
    // must never block each other.
    private final ReentrancyGuard batchGuard = new ReentrancyGuard();
    private final Duration syncInterval;
    private final Duration manualCooldown;

    public StatsSyncService(StatsSourceRepository sourceRepository,
                            MediaKitRepository mediaKitRepository,
                            MediaKitAccess access,
                            StatsProviderRegistry providers,
                            StatsService statsService,
                            PlanPolicy planPolicy,
                            OperationalMetrics metrics,
                            TransactionTemplate transactionTemplate,
                            @Value("${app.statsync.sync-interval-ms:86400000}") long syncIntervalMs,
                            @Value("${app.statsync.manual-cooldown-ms:60000}") long manualCooldownMs) {
        this.sourceRepository = sourceRepository;
        this.mediaKitRepository = mediaKitRepository;
        this.access = access;
        this.providers = providers;
        this.statsService = statsService;
        this.planPolicy = planPolicy;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
        this.syncInterval = Duration.ofMillis(syncIntervalMs);
        this.manualCooldown = Duration.ofMillis(manualCooldownMs);
    }

    // --- owner-facing (all guarded by requireOwnedKit) ---

    /**
     * Connects (or re-points) a platform source. The external account is
     * validated by fetching it immediately — which also lands the first data
     * point. A bad handle fails loudly here and stores nothing.
     *
     * <p>The upstream call happens BEFORE the transaction opens rather than
     * inside it. Holding a database transaction open across a network call is
     * the thing the publish path was built to avoid, and this method was quietly
     * doing it: a slow YouTube response pinned a connection from a pool of five.
     * Validating first also keeps the old behaviour that a bad handle stores
     * nothing, since there is no transaction to roll back yet.
     *
     * <p>The write is then retried, because find-or-create races with itself:
     * two requests for the same kit and platform -- a double-clicked connect
     * button -- can both miss the row and both insert, and one loses to
     * uq_stats_sources_kit_platform. On the second attempt the row is there and
     * the branch that updates it runs instead.
     */
    public SyncSourceResponse connect(String userEmail, Long kitId, Platform platform, String externalId) {
        String handle = externalId.trim();
        Long ownedKitId = transactionTemplate.execute(status ->
                access.requireOwnedKit(userEmail, kitId).getId());
        StatsProvider provider = providers.forPlatform(platform)
                .orElseThrow(SyncNotConfiguredException::new);
        FetchedStats fetched = fetchForCaller(provider, handle);

        return ConstraintRetry.retrying(() -> transactionTemplate.execute(status -> {
            StatsSource source = sourceRepository
                    .findByMediaKitIdAndPlatform(ownedKitId, platform)
                    .orElseGet(() -> new StatsSource(ownedKitId, platform, handle));
            source.updateExternalId(handle);
            source.recordSuccess(Instant.now());
            sourceRepository.saveAndFlush(source);
            append(ownedKitId, platform, fetched);
            return SyncSourceResponse.from(source);
        }));
    }

    @Transactional(readOnly = true)
    public SyncStatusResponse status(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        User owner = access.requireUser(userEmail);
        return new SyncStatusResponse(
                providers.availablePlatforms().stream().map(Enum::name).toList(),
                planPolicy.autoSyncEnabled(owner.getPlan()),
                sourceRepository.findByMediaKitIdOrderByPlatformAsc(kit.getId())
                        .stream().map(SyncSourceResponse::from).toList());
    }

    @Transactional
    public void disconnect(String userEmail, Long kitId, Platform platform) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        StatsSource source = sourceRepository.findByMediaKitIdAndPlatform(kit.getId(), platform)
                .orElseThrow(SyncSourceNotFoundException::new);
        sourceRepository.delete(source);
    }

    /**
     * Owner-triggered refresh. Success and provider failure both return the
     * source (failure filed into lastError) — only cooldown and a missing
     * source are HTTP errors.
     */
    @Transactional
    public SyncSourceResponse syncNow(String userEmail, Long kitId, Platform platform) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        StatsSource source = sourceRepository.findByMediaKitIdAndPlatform(kit.getId(), platform)
                .orElseThrow(SyncSourceNotFoundException::new);
        if (isWithinCooldown(source)) {
            throw new SyncCooldownException();
        }
        syncSource(source);
        return SyncSourceResponse.from(source);
    }

    /**
     * Whether a manual sync is still throttled.
     *
     * <p>A zero cooldown means "no cooldown", and has to be spelled out rather
     * than left to arithmetic. Subtracting Duration.ZERO reduces the comparison
     * to {@code lastSyncedAt.isAfter(now)}, which is a window of no width: the
     * outcome then depends on nothing but clock resolution and how the timestamp
     * happened to be rounded on its way through the database. That is not a
     * throttle, it is a coin flip — and it made the manual-sync test fail
     * roughly one full run in two, which is exactly the kind of failure people
     * learn to re-run instead of read.
     */
    private boolean isWithinCooldown(StatsSource source) {
        if (manualCooldown.isZero() || manualCooldown.isNegative()) {
            return false;
        }
        return source.getLastSyncedAt() != null
                && source.getLastSyncedAt().isAfter(Instant.now().minus(manualCooldown));
    }

    // --- scheduled batch ---

    /**
     * Refreshes all due sources of PRO owners. Overlap-guarded; each source in
     * its own transaction with its own try/catch; a QUOTA failure aborts the
     * remainder of the batch (the budget is global, hammering on is pointless).
     *
     * @return sources attempted, or -1 if skipped (already running).
     */
    public int runSyncBatch() {
        int[] attempted = {0};
        boolean ran = batchGuard.tryRun(() -> {
            List<Long> dueIds = transactionTemplate.execute(status ->
                    sourceRepository.findDueIds(Instant.now().minus(syncInterval)));
            if (dueIds == null) {
                return;
            }
            for (Long id : dueIds) {
                try {
                    StatsProviderException.Kind failure = transactionTemplate.execute(
                            status -> syncEligibleById(id));
                    if (failure != null) {
                        attempted[0]++;
                        metrics.statsSyncSourceFailed();
                        if (failure == StatsProviderException.Kind.QUOTA) {
                            // Every remaining source stays stale until the quota
                            // resets, and the batch says nothing else about it.
                            metrics.statsSyncQuotaExhausted();
                            log.error("Stats sync batch aborted after {} sources: provider quota "
                                    + "exhausted, the rest stay stale until it resets", attempted[0]);
                            break;
                        }
                    } else {
                        attempted[0]++;
                    }
                } catch (SkippedSourceException e) {
                    // Not an attempt: FREE owner, deleted kit or vanished provider.
                } catch (Exception e) {
                    metrics.statsSyncSourceFailed();
                    log.warn("Stats sync failed for source {}: {}", id, e.getMessage());
                }
            }
        });
        return ran ? attempted[0] : -1;
    }

    /** @return the failure kind, null on success; throws {@link SkippedSourceException} when ineligible. */
    private StatsProviderException.Kind syncEligibleById(Long id) {
        StatsSource source = sourceRepository.findById(id)
                .filter(StatsSource::isEnabled)
                .orElseThrow(SkippedSourceException::new);
        MediaKit kit = mediaKitRepository.findById(source.getMediaKitId())
                .orElseThrow(SkippedSourceException::new);
        User owner = access.requireOwner(kit);
        if (!planPolicy.autoSyncEnabled(owner.getPlan())) {
            throw new SkippedSourceException();
        }
        return syncSource(source);
    }

    // --- shared internals ---

    /** Fetch + append; failures land in lastError (never thrown). @return failure kind or null. */
    private StatsProviderException.Kind syncSource(StatsSource source) {
        StatsProvider provider = providers.forPlatform(source.getPlatform()).orElse(null);
        if (provider == null) {
            source.recordFailure("provider not configured");
            return StatsProviderException.Kind.TRANSIENT;
        }
        try {
            FetchedStats fetched = provider.fetch(source.getExternalId());
            append(source.getMediaKitId(), source.getPlatform(), fetched);
            source.recordSuccess(Instant.now());
            return null;
        } catch (StatsProviderException e) {
            source.recordFailure(e.getMessage());
            return e.kind();
        }
    }

    /** Connect-time fetch: the caller is waiting, so failures become HTTP errors. */
    private FetchedStats fetchForCaller(StatsProvider provider, String externalId) {
        try {
            return provider.fetch(externalId);
        } catch (StatsProviderException e) {
            if (e.kind() == StatsProviderException.Kind.NOT_FOUND) {
                throw new ExternalAccountNotFoundException(e.getMessage());
            }
            throw new SyncUpstreamException(e.getMessage());
        }
    }

    private void append(Long kitId, Platform platform, FetchedStats fetched) {
        statsService.recordForKit(kitId, new RecordStatsRequest(
                platform, fetched.followers(), fetched.avgViews(),
                fetched.avgLikes(), fetched.avgComments()));
    }

    /** Batch-internal control flow: this source is not eligible this round. */
    private static class SkippedSourceException extends RuntimeException {
    }
}
