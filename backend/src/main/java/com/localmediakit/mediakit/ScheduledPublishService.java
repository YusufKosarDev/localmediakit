package com.localmediakit.mediakit;

import com.localmediakit.domain.ReentrancyGuard;
import com.localmediakit.observability.OperationalMetrics;
import com.localmediakit.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Publishing at a chosen moment.
 *
 * <p><b>This does not change the publish path.</b> When the moment arrives the
 * job calls the same {@link MediaKitPublicationService#publish} the button
 * calls -- same plan gate, same snapshot, same edge revalidation. Scheduling is
 * a caller, not a second way to publish, which is what keeps the product's
 * central guarantee a single piece of code with a single set of tests.
 *
 * <p>It follows that the snapshot is taken when the moment arrives, not when
 * the schedule was set. That is the same rule as pressing the button, moved in
 * time, and it is the one people expect: a correction made in between should go
 * out rather than be discarded by yesterday's decision.
 */
@Service
public class ScheduledPublishService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublishService.class);

    private final MediaKitAccess access;
    private final MediaKitRepository mediaKitRepository;
    private final MediaKitPublicationService publicationService;
    private final OperationalMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    /** Its own instance: this batch and the other jobs must not block each other. */
    private final ReentrancyGuard batchGuard = new ReentrancyGuard();

    public ScheduledPublishService(MediaKitAccess access,
                                   MediaKitRepository mediaKitRepository,
                                   MediaKitPublicationService publicationService,
                                   OperationalMetrics metrics,
                                   TransactionTemplate transactionTemplate) {
        this.access = access;
        this.mediaKitRepository = mediaKitRepository;
        this.publicationService = publicationService;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
    }

    /** Arms a publish. The time has to be in the future; the past is just "publish". */
    @Transactional
    public MediaKitResponse schedule(String userEmail, Long kitId, Instant when) {
        if (when == null || !when.isAfter(Instant.now())) {
            throw new InvalidScheduleException();
        }
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        kit.schedulePublishAt(when);
        return MediaKitResponse.from(kit, null, kit.isPasswordProtected());
    }

    @Transactional
    public MediaKitResponse cancel(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        kit.clearSchedule();
        return MediaKitResponse.from(kit, null, kit.isPasswordProtected());
    }

    /**
     * Publishes everything whose moment has passed.
     *
     * @return kits published, or -1 if skipped because a run is in progress
     */
    public int runDueBatch() {
        int[] published = {0};
        boolean ran = batchGuard.tryRun(() -> {
            List<Long> dueIds = transactionTemplate.execute(status ->
                    mediaKitRepository
                            .findByScheduledPublishAtLessThanEqualOrderByScheduledPublishAtAsc(Instant.now())
                            .stream().map(MediaKit::getId).toList());
            if (dueIds == null) {
                return;
            }
            for (Long kitId : dueIds) {
                if (publishOne(kitId)) {
                    published[0]++;
                }
            }
        });
        return ran ? published[0] : -1;
    }

    /**
     * One kit, with the schedule cleared and committed BEFORE the publish runs.
     *
     * <p>The order is the important part. If the publish throws, the schedule
     * is already spent, so the next tick does not try the same thing again -- a
     * scheduled publish firing repeatedly into the same plan limit would be a
     * loop nobody asked for. The cost is that a crash between the two steps
     * loses the publish rather than repeating it, which is the right way round:
     * a page that did not go live is a message the creator can act on, a page
     * that published itself twice is a version history they cannot explain.
     */
    private boolean publishOne(Long kitId) {
        String ownerEmail;
        try {
            ownerEmail = transactionTemplate.execute(status -> {
                MediaKit kit = mediaKitRepository.findById(kitId).orElse(null);
                if (kit == null || kit.getScheduledPublishAt() == null) {
                    return null;
                }
                User owner = access.requireOwner(kit);
                kit.clearSchedule();
                return owner.getEmail();
            });
        } catch (RuntimeException e) {
            log.warn("Scheduled publish could not resolve kit {}: {}", kitId, e.getMessage());
            return false;
        }
        if (ownerEmail == null) {
            return false;
        }

        try {
            publicationService.publish(ownerEmail, kitId);
            metrics.scheduledPublishCompleted();
            log.info("Scheduled publish completed for kit {}", kitId);
            return true;
        } catch (RuntimeException e) {
            // The creator believes their page went live at the time they chose.
            // If it did not, the reason has to end up somewhere they will see it.
            metrics.scheduledPublishFailed();
            log.error("Scheduled publish failed for kit {}: {}", kitId, e.getMessage());
            transactionTemplate.executeWithoutResult(status ->
                    mediaKitRepository.findById(kitId).ifPresent(k -> k.failSchedule(e.getMessage())));
            return false;
        }
    }
}
