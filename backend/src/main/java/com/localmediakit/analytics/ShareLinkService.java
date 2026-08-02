package com.localmediakit.analytics;

import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import com.localmediakit.shared.ConstraintRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Labelled share links and what came back through them.
 *
 * <p>This is the half of the product that was missing. The page could be sent
 * to a brand and the brand could read it, and the creator saw a number. Which
 * brand it was is something only the creator knows, so the label is theirs to
 * write when they create the link -- nothing here learns anything about a
 * visitor that the anonymous fingerprint did not already refuse to learn.
 */
@Service
public class ShareLinkService {

    private final KitShareLinkRepository shareLinkRepository;
    private final PageViewRepository pageViewRepository;
    private final MediaKitAccess access;
    private final TransactionTemplate transactionTemplate;
    private final int maxPerKit;

    public ShareLinkService(KitShareLinkRepository shareLinkRepository,
                            PageViewRepository pageViewRepository,
                            MediaKitAccess access,
                            TransactionTemplate transactionTemplate,
                            @Value("${app.share-links.max-per-kit:50}") int maxPerKit) {
        this.shareLinkRepository = shareLinkRepository;
        this.pageViewRepository = pageViewRepository;
        this.access = access;
        this.transactionTemplate = transactionTemplate;
        this.maxPerKit = maxPerKit;
    }

    /**
     * Creates a link. Retried on a constraint violation for the same reason
     * every other generated-value write here is: the token is random, so a
     * collision is astronomically unlikely and completely silent when it
     * happens -- a second attempt simply generates another one.
     */
    public ShareLinkResponse create(String userEmail, Long kitId, String label) {
        return ConstraintRetry.retrying(() -> transactionTemplate.execute(status -> {
            MediaKit kit = access.requireOwnedKit(userEmail, kitId);
            if (shareLinkRepository.countByMediaKitId(kit.getId()) >= maxPerKit) {
                throw new TooManyShareLinksException(maxPerKit);
            }
            KitShareLink link = new KitShareLink(kit.getId(), label.trim());
            shareLinkRepository.saveAndFlush(link);
            return ShareLinkResponse.from(link, kit.getSlug(), 0, 0);
        }));
    }

    /**
     * The links with their view counts.
     *
     * <p>Counts are aggregated in one query rather than per link: the obvious
     * version asks page_views once per row, which is the shape this codebase
     * already had to fix once on the kit list.
     */
    @Transactional(readOnly = true)
    public List<ShareLinkResponse> list(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        Map<Long, long[]> counts = pageViewRepository.countsByShareLink(kit.getId()).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()},
                        (a, b) -> a));

        return shareLinkRepository.findByMediaKitIdOrderByCreatedAtDesc(kit.getId()).stream()
                .map(link -> {
                    long[] seen = counts.getOrDefault(link.getId(), new long[]{0, 0});
                    return ShareLinkResponse.from(link, kit.getSlug(), seen[0], seen[1]);
                })
                .toList();
    }

    @Transactional
    public void revoke(String userEmail, Long kitId, Long linkId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        KitShareLink link = shareLinkRepository.findByIdAndMediaKitId(linkId, kit.getId())
                .orElseThrow(ShareLinkNotFoundException::new);
        link.revoke();
    }

    /**
     * Ingestion-side resolution: token to link id, for this kit only.
     *
     * <p>Every failure is silent and returns null, which leaves the view
     * counted but unattributed. A token that is unknown, revoked, or belongs to
     * a different kit is a visitor who is really there -- refusing the visit
     * because its label did not check out would be losing the data to protect
     * a footnote about it.
     */
    Long resolveForKit(String token, Long mediaKitId) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return shareLinkRepository.findByToken(token.trim())
                .filter(KitShareLink::isActive)
                .filter(link -> link.getMediaKitId().equals(mediaKitId))
                .map(KitShareLink::getId)
                .orElse(null);
    }

    /** Exposed for the analytics payload's per-link breakdown. */
    @Transactional(readOnly = true)
    public Map<Long, String> labelsFor(Long mediaKitId) {
        return shareLinkRepository.findByMediaKitIdOrderByCreatedAtDesc(mediaKitId).stream()
                .collect(Collectors.toMap(KitShareLink::getId, KitShareLink::getLabel,
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }
}
