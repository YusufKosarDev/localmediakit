package com.localmediakit.mediakit;

import com.localmediakit.shared.ConstraintRetry;
import com.localmediakit.user.PlanPolicy;
import com.localmediakit.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MediaKitService {

    private final MediaKitRepository mediaKitRepository;
    private final MediaKitVersionRepository versionRepository;
    private final MediaKitAccess access;
    private final SlugService slugService;
    private final PlanPolicy planPolicy;
    private final RevalidationClient revalidationClient;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;

    public MediaKitService(MediaKitRepository mediaKitRepository,
                           MediaKitVersionRepository versionRepository,
                           MediaKitAccess access,
                           SlugService slugService,
                           PlanPolicy planPolicy,
                           RevalidationClient revalidationClient,
                           TransactionTemplate transactionTemplate,
                           PasswordEncoder passwordEncoder) {
        this.mediaKitRepository = mediaKitRepository;
        this.versionRepository = versionRepository;
        this.access = access;
        this.slugService = slugService;
        this.planPolicy = planPolicy;
        this.revalidationClient = revalidationClient;
        this.transactionTemplate = transactionTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a kit. The slug is chosen by reading which ones are taken, so two
     * requests racing on the same title can both settle on the same free slug
     * and one of them loses to the unique constraint. Retrying re-reads, finds
     * the committed row, and takes the suffix the collision logic was written to
     * produce -- which is what the loser should have got in the first place.
     */
    public MediaKitResponse create(String userEmail, CreateMediaKitRequest request) {
        return ConstraintRetry.retrying(() -> transactionTemplate.execute(status -> {
            User user = access.requireUser(userEmail);
            planPolicy.assertCanCreateMediaKit(user.getPlan(), mediaKitRepository.countByUserId(user.getId()));

            String slug = resolveSlug(request.slug(), request.title(), null);
            MediaKit kit = new MediaKit(
                    user.getId(), slug, request.title().trim(),
                    request.headline(), request.avatarUrl(),
                    request.theme(), request.accent(), request.layout(),
                    // A new kit defaults to the language the owner administers in;
                    // a sensible starting point, still per-kit and overridable.
                    request.language() == null || request.language().isBlank()
                            ? user.getLocale() : request.language());
            // Flush inside the attempt: without it the insert happens at commit,
            // outside this try, and the violation escapes the retry entirely.
            mediaKitRepository.saveAndFlush(kit);
            return toResponse(kit);
        }));
    }

    @Transactional(readOnly = true)
    public List<MediaKitResponse> list(String userEmail) {
        User user = access.requireUser(userEmail);
        List<MediaKit> kits = mediaKitRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        // Resolve every kit's published slug in ONE query instead of N.
        List<Long> versionIds = kits.stream()
                .map(MediaKit::getPublishedVersionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, String> slugByVersionId = versionIds.isEmpty()
                ? Map.of()
                : versionRepository.findByIdIn(versionIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                MediaKitVersion::getId, MediaKitVersion::getSlug));

        return kits.stream().map(kit -> {
            String publishedSlug = kit.getPublishedVersionId() == null
                    ? null
                    : slugByVersionId.get(kit.getPublishedVersionId());
            return MediaKitResponse.from(kit, publishedSlug, kit.isPasswordProtected());
        }).toList();
    }

    @Transactional(readOnly = true)
    public MediaKitResponse get(String userEmail, Long id) {
        return toResponse(access.requireOwnedKit(userEmail, id));
    }

    /**
     * Edits only the DRAFT. The published snapshot (and so the public page) is
     * untouched until the owner explicitly publishes again.
     */
    /** Edits only the DRAFT; retried for the same slug race as {@link #create}. */
    public MediaKitResponse update(String userEmail, Long id, UpdateMediaKitRequest request) {
        return ConstraintRetry.retrying(() -> transactionTemplate.execute(status -> {
            MediaKit kit = access.requireOwnedKit(userEmail, id);
            kit.updateDetails(request.title().trim(), request.headline(), request.avatarUrl(),
                    request.theme(), request.accent(), request.layout(),
                    request.language() == null || request.language().isBlank()
                            ? kit.getLanguage() : request.language());
            if (request.contactEnabled() != null) {
                kit.setContactEnabled(request.contactEnabled());
            }

            if (request.slug() != null && !request.slug().isBlank()) {
                String desired = slugService.slugify(request.slug());
                if (!desired.equals(kit.getSlug())) {
                    if (slugService.isReserved(desired)) {
                        throw new ReservedSlugException(desired);
                    }
                    String unique = slugService.makeUnique(desired, candidate -> slugTaken(candidate, id));
                    kit.changeSlug(unique);
                }
            }
            mediaKitRepository.saveAndFlush(kit);
            return toResponse(kit);
        }));
    }

    /**
     * Sets the draft access password (PRO only). Affects the public page only
     * on the next publish, keeping the immutable-snapshot contract.
     */
    @Transactional
    public void setPassword(String userEmail, Long id, String rawPassword) {
        User user = access.requireUser(userEmail);
        planPolicy.assertPasswordProtectionAllowed(user.getPlan());
        MediaKit kit = access.requireOwnedKit(userEmail, id);
        kit.setPasswordHash(passwordEncoder.encode(rawPassword));
    }

    @Transactional
    public void removePassword(String userEmail, Long id) {
        MediaKit kit = access.requireOwnedKit(userEmail, id);
        kit.setPasswordHash(null);
    }

    public void delete(String userEmail, Long id) {
        // Capture the live slug inside the transaction; revalidate after commit
        // so the public page is evicted only once the kit is really gone.
        Optional<String> liveSlug = transactionTemplate.execute(status -> {
            MediaKit kit = access.requireOwnedKit(userEmail, id);
            Optional<String> active = kit.getPublishedVersionId() == null
                    ? Optional.<String>empty()
                    : versionRepository.findById(kit.getPublishedVersionId()).map(MediaKitVersion::getSlug);
            // Detach the pointer before the row goes away, so the FK to
            // media_kit_versions never blocks the cascade delete.
            kit.clearPublishedVersion();
            mediaKitRepository.saveAndFlush(kit);
            mediaKitRepository.delete(kit);
            return active;
        });
        liveSlug.ifPresent(revalidationClient::revalidate);
    }

    /**
     * Resolves a slug from an explicit value (if provided) or the title.
     * An explicitly provided reserved slug is rejected; collisions get a suffix.
     */
    private String resolveSlug(String explicitSlug, String title, Long excludeId) {
        boolean explicit = explicitSlug != null && !explicitSlug.isBlank();
        String base = slugService.slugify(explicit ? explicitSlug : title);
        if (explicit && slugService.isReserved(base)) {
            throw new ReservedSlugException(base);
        }
        return slugService.makeUnique(base, candidate -> slugTaken(candidate, excludeId));
    }

    /**
     * A slug is taken if another DRAFT uses it or if it is another kit's LIVE
     * published URL (drafts can be renamed after publish, so the two differ).
     */
    private boolean slugTaken(String candidate, Long excludeKitId) {
        boolean draftTaken = excludeKitId == null
                ? mediaKitRepository.existsBySlug(candidate)
                : mediaKitRepository.existsBySlugAndIdNot(candidate, excludeKitId);
        return draftTaken
                || versionRepository.activeSlugTakenByOtherKit(candidate, excludeKitId == null ? -1L : excludeKitId);
    }

    private MediaKitResponse toResponse(MediaKit kit) {
        String publishedSlug = kit.getPublishedVersionId() == null
                ? null
                : versionRepository.findById(kit.getPublishedVersionId())
                        .map(MediaKitVersion::getSlug).orElse(null);
        return MediaKitResponse.from(kit, publishedSlug, kit.isPasswordProtected());
    }
}
