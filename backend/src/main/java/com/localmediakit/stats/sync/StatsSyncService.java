package com.localmediakit.stats.sync;

import com.localmediakit.domain.ReentrancyGuard;
import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import com.localmediakit.mediakit.MediaKitRepository;
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
                            TransactionTemplate transactionTemplate,
                            @Value("${app.statsync.sync-interval-ms:86400000}") long syncIntervalMs,
                            @Value("${app.statsync.manual-cooldown-ms:60000}") long manualCooldownMs) {
        this.sourceRepository = sourceRepository;
        this.mediaKitRepository = mediaKitRepository;
        this.access = access;
        this.providers = providers;
        this.statsService = statsService;
        this.planPolicy = planPolicy;
        this.transactionTemplate = transactionTemplate;
        this.syncInterval = Duration.ofMillis(syncIntervalMs);
        this.manualCooldown = Duration.ofMillis(manualCooldownMs);
    }

    // --- owner-facing (all guarded by requireOwnedKit) ---

    /**
     * Connects (or re-points) a platform source. The external account is
     * validated by fetching it immediately — which also lands the first data
     * point. A bad handle fails loudly here and stores nothing.
     */
    @Transactional
    public SyncSourceResponse connect(String userEmail, Long kitId, Platform platform, String externalId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        StatsProvider provider = providers.forPlatform(platform)
                .orElseThrow(SyncNotConfiguredException::new);
        FetchedStats fetched = fetchForCaller(provider, externalId.trim());

        StatsSource source = sourceRepository
                .findByMediaKitIdAndPlatform(kit.getId(), platform)
                .orElseGet(() -> sourceRepository.save(new StatsSource(kit.getId(), platform, externalId.trim())));
        source.updateExternalId(externalId.trim());
        append(kit.getId(), platform, fetched);
        source.recordSuccess(Instant.now());
        return SyncSourceResponse.from(source);
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
        if (source.getLastSyncedAt() != null
                && source.getLastSyncedAt().isAfter(Instant.now().minus(manualCooldown))) {
            throw new SyncCooldownException();
        }
        syncSource(source);
        return SyncSourceResponse.from(source);
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
                        if (failure == StatsProviderException.Kind.QUOTA) {
                            log.warn("Stats sync batch aborted: provider quota exhausted");
                            break;
                        }
                    } else {
                        attempted[0]++;
                    }
                } catch (SkippedSourceException e) {
                    // Not an attempt: FREE owner, deleted kit or vanished provider.
                } catch (Exception e) {
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
