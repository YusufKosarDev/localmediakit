package com.localmediakit.user;

import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitRepository;
import com.localmediakit.mediakit.MediaKitVersion;
import com.localmediakit.mediakit.MediaKitVersionRepository;
import com.localmediakit.stats.PlatformStatsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Getting-started state for the signed-in user.
 *
 * <p>Computed here rather than on {@code MediaKitResponse} so the kit-listing
 * path keeps its existing shape and query count — onboarding is a dashboard
 * concern, not part of what a media kit is.
 */
@Service
public class OnboardingService {

    private final UserRepository userRepository;
    private final MediaKitRepository mediaKitRepository;
    private final MediaKitVersionRepository versionRepository;
    private final PlatformStatsRepository platformStatsRepository;
    private final String sharedDemoEmail;

    public OnboardingService(UserRepository userRepository,
                             MediaKitRepository mediaKitRepository,
                             MediaKitVersionRepository versionRepository,
                             PlatformStatsRepository platformStatsRepository,
                             @Value("${app.demo.email:demo@localmediakit.app}") String sharedDemoEmail) {
        this.userRepository = userRepository;
        this.mediaKitRepository = mediaKitRepository;
        this.versionRepository = versionRepository;
        this.platformStatsRepository = platformStatsRepository;
        this.sharedDemoEmail = sharedDemoEmail;
    }

    @Transactional(readOnly = true)
    public OnboardingResponse status(String email) {
        User user = require(email);
        List<MediaKit> kits = mediaKitRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        if (kits.isEmpty()) {
            return new OnboardingResponse(dismissedFor(user), false, false, false, null);
        }

        List<Long> kitIds = kits.stream().map(MediaKit::getId).toList();
        boolean hasStats = platformStatsRepository.existsByMediaKitIdIn(kitIds);

        List<Long> liveVersionIds = kits.stream()
                .map(MediaKit::getPublishedVersionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        String publicSlug = liveVersionIds.isEmpty() ? null : versionRepository.findByIdIn(liveVersionIds)
                .stream().map(MediaKitVersion::getSlug).findFirst().orElse(null);

        return new OnboardingResponse(dismissedFor(user), true, hasStats, publicSlug != null, publicSlug);
    }

    /**
     * Records the dismissal — except on the shared demo account, where it is
     * deliberately dropped.
     *
     * <p>The demo is one account browsed by a stream of different people. If
     * the first visitor's dismissal were stored, everyone after them would
     * land on an unexplained dashboard. Leaving it unstored means each new
     * visitor is introduced to the product; the client suppresses repeats
     * within a single browser, which is the right granularity here.
     */
    @Transactional
    public void dismiss(String email) {
        User user = require(email);
        if (isSharedDemo(user)) {
            return;
        }
        user.dismissOnboarding();
    }

    /** The demo never reports itself as dismissed, for the same reason. */
    private boolean dismissedFor(User user) {
        return !isSharedDemo(user) && user.getOnboardingCompletedAt() != null;
    }

    private boolean isSharedDemo(User user) {
        return user.getEmail().equalsIgnoreCase(sharedDemoEmail);
    }

    private User require(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
