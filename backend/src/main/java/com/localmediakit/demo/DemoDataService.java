package com.localmediakit.demo;

import com.localmediakit.billing.SubscriptionRepository;
import com.localmediakit.collab.CollaborationRequest;
import com.localmediakit.collab.CollaborationService;
import com.localmediakit.mediakit.CreateMediaKitRequest;
import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitRepository;
import com.localmediakit.mediakit.MediaKitResponse;
import com.localmediakit.mediakit.MediaKitService;
import com.localmediakit.mediakit.MediaKitPublicationService;
import com.localmediakit.ratecard.RateCardRequest;
import com.localmediakit.ratecard.RateCardService;
import com.localmediakit.stats.DemographicCategory;
import com.localmediakit.stats.DemographicEntry;
import com.localmediakit.stats.DemographicsService;
import com.localmediakit.stats.Platform;
import com.localmediakit.stats.PlatformStats;
import com.localmediakit.stats.PlatformStatsRepository;
import com.localmediakit.stats.UpdateDemographicsRequest;
import com.localmediakit.user.Plan;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Seeds a rich, login-able demo account so a reviewer can explore the full
 * dashboard, and cleans up accumulated throwaway test accounts. Everything is
 * idempotent so it can run on every startup and on a nightly reset.
 */
@Service
public class DemoDataService {

    /** Public demo credentials (this is a demo — the login is meant to be shared). */
    public static final String DEMO_EMAIL = "demo@localmediakit.app";
    public static final String DEMO_PASSWORD = "demo1234";
    private static final String TEST_EMAIL_SUFFIX = "@test.dev";

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MediaKitRepository mediaKitRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlatformStatsRepository platformStatsRepository;
    private final MediaKitService mediaKitService;
    private final DemographicsService demographicsService;
    private final CollaborationService collaborationService;
    private final RateCardService rateCardService;
    private final MediaKitPublicationService publicationService;

    public DemoDataService(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           MediaKitRepository mediaKitRepository,
                           SubscriptionRepository subscriptionRepository,
                           PlatformStatsRepository platformStatsRepository,
                           MediaKitService mediaKitService,
                           DemographicsService demographicsService,
                           CollaborationService collaborationService,
                           RateCardService rateCardService,
                           MediaKitPublicationService publicationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mediaKitRepository = mediaKitRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.platformStatsRepository = platformStatsRepository;
        this.mediaKitService = mediaKitService;
        this.demographicsService = demographicsService;
        this.collaborationService = collaborationService;
        this.rateCardService = rateCardService;
        this.publicationService = publicationService;
    }

    /** Deletes throwaway accounts (email ending in @test.dev) and their data. */
    @Transactional
    public int cleanupTestUsers() {
        List<User> testUsers = userRepository.findByEmailEndingWith(TEST_EMAIL_SUFFIX);
        for (User user : testUsers) {
            deleteUsersKits(user.getId());
            subscriptionRepository.findByUserId(user.getId()).ifPresent(subscriptionRepository::delete);
            userRepository.delete(user);
        }
        return testUsers.size();
    }

    /** Rebuilds the demo account's kit from scratch so the demo stays clean. */
    @Transactional
    public void resetDemo() {
        User demo = ensureDemoUser();
        deleteUsersKits(demo.getId());

        String email = demo.getEmail();
        MediaKitResponse kit = mediaKitService.create(email, new CreateMediaKitRequest(
                "Ornek Medya Kiti",
                "Seyahat ve yasam tarzi icerik ureticisi",
                null, "light", "ornek-medya-kiti"));
        Long kitId = kit.id();

        seedStats(kitId);
        demographicsService.replace(email, kitId, new UpdateDemographicsRequest(List.of(
                new DemographicEntry(DemographicCategory.AGE, "18-24", new BigDecimal("40")),
                new DemographicEntry(DemographicCategory.AGE, "25-34", new BigDecimal("38")),
                new DemographicEntry(DemographicCategory.AGE, "35-44", new BigDecimal("15")),
                new DemographicEntry(DemographicCategory.AGE, "45+", new BigDecimal("7")),
                new DemographicEntry(DemographicCategory.GENDER, "Kadin", new BigDecimal("55")),
                new DemographicEntry(DemographicCategory.GENDER, "Erkek", new BigDecimal("45")),
                new DemographicEntry(DemographicCategory.COUNTRY, "Turkiye", new BigDecimal("70")),
                new DemographicEntry(DemographicCategory.COUNTRY, "Almanya", new BigDecimal("12")),
                new DemographicEntry(DemographicCategory.COUNTRY, "ABD", new BigDecimal("7")),
                new DemographicEntry(DemographicCategory.COUNTRY, "Diger", new BigDecimal("11")))));
        collaborationService.create(email, kitId, new CollaborationRequest(
                "Kahve Diyari", "Yeni urun lansmani", "2025 Q4", "1,1M goruntulenme", null, 0));
        collaborationService.create(email, kitId, new CollaborationRequest(
                "GezginApp", "Seyahat vlog serisi", "2025 Q3", "3 video, 1,8M izlenme", null, 1));
        rateCardService.create(email, kitId, new RateCardRequest(
                "YouTube video sponsorlugu", new BigDecimal("25000"), "TRY", "60 sn'ye kadar entegrasyon", 0));
        rateCardService.create(email, kitId, new RateCardRequest(
                "Instagram Reels", new BigDecimal("9000"), "TRY", null, 1));
        rateCardService.create(email, kitId, new RateCardRequest(
                "TikTok video", new BigDecimal("12000"), "TRY", null, 2));

        publicationService.publish(email, kitId);
        log.info("Demo account reset: kit '{}' published", kit.slug());
    }

    private void seedStats(Long kitId) {
        Instant now = Instant.now();
        Instant baseline = now.minus(35, ChronoUnit.DAYS);
        // Backdated baselines give the 30-day growth badge something to compare.
        platformStatsRepository.save(new PlatformStats(kitId, Platform.YOUTUBE, 90000L, 40000L, 2800L, 180L, baseline));
        platformStatsRepository.save(new PlatformStats(kitId, Platform.YOUTUBE, 102000L, 44000L, 3100L, 210L, now));
        platformStatsRepository.save(new PlatformStats(kitId, Platform.INSTAGRAM, 60000L, null, 3500L, 300L, baseline));
        platformStatsRepository.save(new PlatformStats(kitId, Platform.INSTAGRAM, 64000L, null, 3800L, 340L, now));
        platformStatsRepository.save(new PlatformStats(kitId, Platform.TIKTOK, 120000L, 60000L, 8000L, 500L, baseline));
        platformStatsRepository.save(new PlatformStats(kitId, Platform.TIKTOK, 145000L, 72000L, 9500L, 640L, now));
    }

    private User ensureDemoUser() {
        return userRepository.findByEmail(DEMO_EMAIL).orElseGet(() -> {
            User user = new User(DEMO_EMAIL, passwordEncoder.encode(DEMO_PASSWORD), "Demo Kullanici");
            user.changePlan(Plan.PRO);
            return userRepository.save(user);
        });
    }

    private void deleteUsersKits(Long userId) {
        for (MediaKit kit : mediaKitRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            // Detach the live pointer so the FK never blocks the cascade delete.
            kit.clearPublishedVersion();
            mediaKitRepository.saveAndFlush(kit);
            mediaKitRepository.delete(kit);
        }
        // Flush the deletes now so a re-created kit's slug-uniqueness check does
        // not see the just-deleted rows (keeps the demo slug stable across resets).
        mediaKitRepository.flush();
    }
}
