package com.localmediakit.mediakit;

import com.localmediakit.collab.BrandCollaboration;
import com.localmediakit.collab.BrandCollaborationRepository;
import com.localmediakit.media.MediaItem;
import com.localmediakit.media.MediaItemRepository;
import com.localmediakit.ratecard.RateCardItem;
import com.localmediakit.ratecard.RateCardItemRepository;
import com.localmediakit.shared.ConstraintRetry;
import com.localmediakit.stats.AudienceDemographic;
import com.localmediakit.stats.AudienceDemographicRepository;
import com.localmediakit.stats.Platform;
import com.localmediakit.stats.PlatformStats;
import com.localmediakit.stats.PlatformStatsRepository;
import com.localmediakit.user.PlanPolicy;
import com.localmediakit.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Copies a kit so a creator can tailor one per brand without retyping
 * everything.
 *
 * <p>The rule, in one sentence: <b>a copy inherits everything that describes
 * the creator, and nothing that describes the original's own life.</b> Stats,
 * audience, collaborations, rates and work come across. Publication, visitors,
 * leads, share links, password and custom domains do not -- those belong to a
 * page that has been out in the world, and the copy has not.
 *
 * <p>The copy is a DRAFT. Duplicating something and finding a second live page
 * on the internet a moment later is the kind of surprise this product's whole
 * publish/draft split exists to prevent.
 */
@Service
public class KitDuplicationService {

    private final MediaKitAccess access;
    private final MediaKitRepository mediaKitRepository;
    private final PlatformStatsRepository statsRepository;
    private final AudienceDemographicRepository demographicRepository;
    private final BrandCollaborationRepository collaborationRepository;
    private final RateCardItemRepository rateCardRepository;
    private final MediaItemRepository mediaRepository;
    private final SlugService slugService;
    private final PlanPolicy planPolicy;
    private final TransactionTemplate transactionTemplate;

    public KitDuplicationService(MediaKitAccess access,
                                 MediaKitRepository mediaKitRepository,
                                 PlatformStatsRepository statsRepository,
                                 AudienceDemographicRepository demographicRepository,
                                 BrandCollaborationRepository collaborationRepository,
                                 RateCardItemRepository rateCardRepository,
                                 MediaItemRepository mediaRepository,
                                 SlugService slugService,
                                 PlanPolicy planPolicy,
                                 TransactionTemplate transactionTemplate) {
        this.access = access;
        this.mediaKitRepository = mediaKitRepository;
        this.statsRepository = statsRepository;
        this.demographicRepository = demographicRepository;
        this.collaborationRepository = collaborationRepository;
        this.rateCardRepository = rateCardRepository;
        this.mediaRepository = mediaRepository;
        this.slugService = slugService;
        this.planPolicy = planPolicy;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * @param title what to call the copy; the source's title when absent. The
     *              client supplies it because it knows which language the
     *              creator is reading, and "(copy)" is not a server concern.
     */
    public MediaKitResponse duplicate(String userEmail, Long sourceKitId, String title) {
        return ConstraintRetry.retrying(() -> transactionTemplate.execute(status -> {
            MediaKit source = access.requireOwnedKit(userEmail, sourceKitId);
            User owner = access.requireUser(userEmail);
            planPolicy.assertCanCreateMediaKit(owner.getPlan(),
                    mediaKitRepository.countByUserId(owner.getId()));

            String copyTitle = title == null || title.isBlank() ? source.getTitle() : title.trim();
            // Through the same collision logic as a fresh kit: the slug is
            // derived from the new title and suffixed until it is free, so a
            // copy can never take the original's published URL.
            String slug = slugService.makeUnique(
                    slugService.slugify(copyTitle),
                    candidate -> mediaKitRepository.existsBySlug(candidate));

            MediaKit copy = new MediaKit(
                    owner.getId(), slug, copyTitle, source.getHeadline(), source.getAvatarUrl(),
                    source.getTheme(), source.getAccent(), source.getLayout(), source.getLanguage());
            copy.setContactEnabled(source.isContactEnabled());
            mediaKitRepository.saveAndFlush(copy);

            copyContent(source.getId(), copy.getId());
            return MediaKitResponse.from(copy, null, copy.isPasswordProtected());
        }));
    }

    private void copyContent(Long sourceId, Long copyId) {
        copyLatestMeasurements(sourceId, copyId);

        demographicRepository.findByMediaKitIdOrderByCategoryAscPercentageDesc(sourceId)
                .forEach(d -> demographicRepository.save(new AudienceDemographic(
                        copyId, d.getCategory(), d.getLabel(), d.getPercentage())));

        collaborationRepository.findByMediaKitIdOrderByDisplayOrderAscIdAsc(sourceId)
                .forEach(c -> collaborationRepository.save(new BrandCollaboration(
                        copyId, c.getBrandName(), c.getCampaign(), c.getPeriod(),
                        c.getResultNote(), c.getLogoUrl(), c.getDisplayOrder())));

        rateCardRepository.findByMediaKitIdOrderByDisplayOrderAscIdAsc(sourceId)
                .forEach(r -> rateCardRepository.save(new RateCardItem(
                        copyId, r.getServiceName(), r.getPriceAmount(), r.getCurrency(),
                        r.getNote(), r.getDisplayOrder())));

        mediaRepository.findByMediaKitIdOrderByDisplayOrderAscIdAsc(sourceId)
                .forEach(m -> mediaRepository.save(new MediaItem(
                        copyId, m.getTitle(), m.getUrl(), m.getThumbnailUrl(),
                        m.getPlatform(), m.getNote(), m.getDisplayOrder())));
    }

    /**
     * The current numbers, recorded as measured now -- not the source's series.
     *
     * <p>platform_stats is append-only: every row asserts that a measurement was
     * taken for that kit at that moment. Copying the history would put rows in
     * it claiming measurements happened for a kit that did not exist, and the
     * growth figure on the public page would then be computed from them.
     *
     * <p>So the copy starts with the right numbers and no trend. That is the
     * truth: it has today's followers and no history yet, and it accumulates
     * one from here like any other kit.
     */
    private void copyLatestMeasurements(Long sourceId, Long copyId) {
        for (Platform platform : statsRepository.platformsWithData(sourceId)) {
            statsRepository.findFirstByMediaKitIdAndPlatformOrderByRecordedAtDescIdDesc(sourceId, platform)
                    .ifPresent(latest -> statsRepository.save(new PlatformStats(
                            copyId, platform, latest.getFollowers(), latest.getAvgViews(),
                            latest.getAvgLikes(), latest.getAvgComments())));
        }
    }
}
