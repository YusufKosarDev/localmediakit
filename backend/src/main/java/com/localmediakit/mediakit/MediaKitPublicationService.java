package com.localmediakit.mediakit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.collab.CollaborationService;
import com.localmediakit.stats.DemographicsService;
import com.localmediakit.stats.StatsService;
import com.localmediakit.user.PlanPolicy;
import com.localmediakit.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Publish / rollback use cases. Database work runs inside a TransactionTemplate
 * and the edge revalidation call happens strictly AFTER commit, so no DB
 * transaction is ever held open across a network call (Step 0 principle).
 */
@Service
public class MediaKitPublicationService {

    private final MediaKitAccess access;
    private final MediaKitRepository mediaKitRepository;
    private final MediaKitVersionRepository versionRepository;
    private final RevalidationClient revalidationClient;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final StatsService statsService;
    private final DemographicsService demographicsService;
    private final CollaborationService collaborationService;
    private final PlanPolicy planPolicy;
    private final PasswordEncoder passwordEncoder;
    private final UnlockRateLimiter unlockRateLimiter;

    public MediaKitPublicationService(MediaKitAccess access,
                                      MediaKitRepository mediaKitRepository,
                                      MediaKitVersionRepository versionRepository,
                                      RevalidationClient revalidationClient,
                                      TransactionTemplate transactionTemplate,
                                      ObjectMapper objectMapper,
                                      StatsService statsService,
                                      DemographicsService demographicsService,
                                      CollaborationService collaborationService,
                                      PlanPolicy planPolicy,
                                      PasswordEncoder passwordEncoder,
                                      UnlockRateLimiter unlockRateLimiter) {
        this.access = access;
        this.mediaKitRepository = mediaKitRepository;
        this.versionRepository = versionRepository;
        this.revalidationClient = revalidationClient;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.statsService = statsService;
        this.demographicsService = demographicsService;
        this.collaborationService = collaborationService;
        this.planPolicy = planPolicy;
        this.passwordEncoder = passwordEncoder;
        this.unlockRateLimiter = unlockRateLimiter;
    }

    /** Freezes the current draft into a new immutable version and makes it the live one. */
    public PublishResponse publish(String userEmail, Long kitId) {
        Activation result = transactionTemplate.execute(status -> {
            MediaKit kit = access.requireOwnedKit(userEmail, kitId);
            User owner = access.requireUser(userEmail);
            // Plan gate: on FREE only the oldest kit(s) within the limit may
            // (re)publish. Existing live pages are untouched by a downgrade.
            planPolicy.assertCanPublish(owner.getPlan(),
                    mediaKitRepository.countByUserIdAndIdLessThan(owner.getId(), kit.getId()));

            int nextNumber = versionRepository.findTopByMediaKitIdOrderByVersionNumberDesc(kit.getId())
                    .map(v -> v.getVersionNumber() + 1)
                    .orElse(1);
            String json = toJson(buildSnapshot(kit, owner));
            // Freeze the current password into the version, like the badge/stats:
            // draft password changes only reach the public page on republish.
            MediaKitVersion version = versionRepository.save(new MediaKitVersion(
                    kit.getId(), nextNumber, kit.getSlug(), json, kit.getPasswordHash()));
            return activate(kit, version);
        });
        return revalidateAndRespond(result);
    }

    /** Rollback: re-points the live pointer at an existing immutable version. */
    public PublishResponse activateVersion(String userEmail, Long kitId, int versionNumber) {
        Activation result = transactionTemplate.execute(status -> {
            MediaKit kit = access.requireOwnedKit(userEmail, kitId);
            User owner = access.requireUser(userEmail);
            MediaKitVersion version = versionRepository
                    .findByMediaKitIdAndVersionNumber(kit.getId(), versionNumber)
                    .orElseThrow(VersionNotFoundException::new);
            // FREE may only roll back within its visible history window; PRO to any.
            long newerCount = versionRepository
                    .countByMediaKitIdAndVersionNumberGreaterThan(kit.getId(), versionNumber);
            if (newerCount >= planPolicy.maxVisibleVersions(owner.getPlan())) {
                throw new VersionNotVisibleException();
            }
            return activate(kit, version);
        });
        return revalidateAndRespond(result);
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> listVersions(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        User owner = access.requireUser(userEmail);
        // Plan-aware: FREE sees only the most recent versions, PRO the full history.
        return versionRepository.findByMediaKitIdOrderByVersionNumberDesc(kit.getId())
                .stream()
                .limit(planPolicy.maxVisibleVersions(owner.getPlan()))
                .map(v -> VersionResponse.from(v, kit.getPublishedVersionId()))
                .toList();
    }

    /**
     * Public read. A public kit returns its full content (edge-cacheable,
     * unchanged path). A protected kit returns only the gate metadata — the
     * sensitive content is never placed here, so it never reaches the edge.
     */
    @Transactional(readOnly = true)
    public Optional<PublicKitResponse> findPublished(String slug) {
        return versionRepository.findActiveBySlug(slug).map(version -> {
            MediaKitSnapshot snapshot = fromJson(version.getContentJson());
            return version.isPasswordProtected()
                    ? PublicKitResponse.locked(snapshot, version)
                    : PublicKitResponse.full(snapshot, version);
        });
    }

    /**
     * Unlocks a protected page. Verifies the password against the ACTIVE
     * version's frozen hash, guarded by a brute-force limiter keyed on the
     * client. A public (unprotected) kit needs no password and returns its
     * content directly.
     */
    @Transactional(readOnly = true)
    public PublicKitResponse unlock(String slug, String password, String clientKey) {
        MediaKitVersion version = versionRepository.findActiveBySlug(slug)
                .orElseThrow(MediaKitNotFoundException::new);
        MediaKitSnapshot snapshot = fromJson(version.getContentJson());
        if (!version.isPasswordProtected()) {
            return PublicKitResponse.full(snapshot, version);
        }
        unlockRateLimiter.checkAllowed(clientKey);
        if (password == null || !passwordEncoder.matches(password, version.getPasswordHash())) {
            unlockRateLimiter.recordFailure(clientKey);
            throw new InvalidKitPasswordException();
        }
        unlockRateLimiter.reset(clientKey);
        return PublicKitResponse.full(snapshot, version);
    }

    /**
     * Freezes the full publishable state: draft fields plus the CURRENT stats
     * (with engagement/growth already computed) and demographics. The public
     * page reads only this, so later stat entries never move a published page.
     *
     * Package-private on purpose: {@link KitPreviewService} renders the exact
     * same assembly from the live draft, so preview and publish can never drift.
     */
    MediaKitSnapshot buildSnapshot(MediaKit kit, User owner) {
        var platforms = statsService.latestForKit(kit.getId()).stream()
                .map(v -> new MediaKitSnapshot.PlatformStatSnapshot(
                        v.platform().name(), v.followers(), v.avgViews(), v.avgLikes(),
                        v.avgComments(), v.engagementRate(), v.followerGrowth30d()))
                .toList();
        var demographics = demographicsService.listForKit(kit.getId()).stream()
                .map(e -> new MediaKitSnapshot.DemographicSnapshot(
                        e.category().name(), e.label(), e.percentage()))
                .toList();
        // listForKit returns display_order ASC; the frozen array order is the showcase order.
        var collaborations = collaborationService.listForKit(kit.getId()).stream()
                .map(c -> new MediaKitSnapshot.CollaborationSnapshot(
                        c.getBrandName(), c.getCampaign(), c.getPeriod(),
                        c.getResultNote(), c.getLogoUrl()))
                .toList();
        return new MediaKitSnapshot(
                kit.getSlug(), kit.getTitle(), kit.getHeadline(), kit.getAvatarUrl(),
                kit.getTheme(), owner.getDisplayName(), platforms, demographics, collaborations,
                planPolicy.showsBranding(owner.getPlan()));
    }

    private Activation activate(MediaKit kit, MediaKitVersion version) {
        // Both the slug going live and the one going stale must be revalidated,
        // so a rename or rollback also evicts the old URL from the edge cache.
        Set<String> slugs = new LinkedHashSet<>();
        slugs.add(version.getSlug());
        previousActiveSlug(kit).ifPresent(slugs::add);
        kit.activateVersion(version.getId());
        mediaKitRepository.save(kit);
        return new Activation(kit.getId(), version, slugs);
    }

    private Optional<String> previousActiveSlug(MediaKit kit) {
        if (kit.getPublishedVersionId() == null) {
            return Optional.empty();
        }
        return versionRepository.findById(kit.getPublishedVersionId()).map(MediaKitVersion::getSlug);
    }

    private PublishResponse revalidateAndRespond(Activation result) {
        int lastStatus = -1;
        for (String slug : result.slugsToRevalidate()) {
            int status = revalidationClient.revalidate(slug);
            if (slug.equals(result.version().getSlug())) {
                lastStatus = status;
            }
        }
        return new PublishResponse(
                result.kitId(),
                result.version().getVersionNumber(),
                result.version().getSlug(),
                result.version().getPublishedAt().toString(),
                lastStatus);
    }

    private String toJson(MediaKitSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize snapshot", e);
        }
    }

    private MediaKitSnapshot fromJson(String json) {
        try {
            return objectMapper.readValue(json, MediaKitSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt snapshot content_json", e);
        }
    }

    private record Activation(Long kitId, MediaKitVersion version, Set<String> slugsToRevalidate) {
    }
}
