package com.localmediakit.stats.engagement;

import com.localmediakit.stats.Platform;
import com.localmediakit.stats.PlatformStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngagementCalculatorTest {

    private final InstagramEngagementCalculator instagram = new InstagramEngagementCalculator();
    private final YouTubeEngagementCalculator youtube = new YouTubeEngagementCalculator();
    private final TikTokEngagementCalculator tiktok = new TikTokEngagementCalculator();

    private PlatformStats stats(Platform platform, long followers, Long views, Long likes, Long comments) {
        return new PlatformStats(1L, platform, followers, views, likes, comments);
    }

    // --- Instagram: (likes + comments) / followers * 100 ---

    @Test
    void instagramDividesInteractionsByFollowers() {
        Optional<BigDecimal> rate = instagram.calculate(
                stats(Platform.INSTAGRAM, 84000, null, 5000L, 460L));
        assertEquals(new BigDecimal("6.50"), rate.orElseThrow());
    }

    @Test
    void instagramWithZeroFollowersIsEmptyNotZero() {
        assertTrue(instagram.calculate(stats(Platform.INSTAGRAM, 0, null, 100L, 10L)).isEmpty());
    }

    // --- YouTube: (likes + comments) / avg_views * 100 ---

    @Test
    void youtubeDividesInteractionsByViews() {
        Optional<BigDecimal> rate = youtube.calculate(
                stats(Platform.YOUTUBE, 125000, 48000L, 3600L, 240L));
        assertEquals(new BigDecimal("8.00"), rate.orElseThrow());
    }

    @Test
    void youtubeWithoutViewsIsEmpty() {
        assertTrue(youtube.calculate(stats(Platform.YOUTUBE, 125000, null, 3600L, 240L)).isEmpty());
        assertTrue(youtube.calculate(stats(Platform.YOUTUBE, 125000, 0L, 3600L, 240L)).isEmpty());
    }

    // --- TikTok: view-based, falls back to followers when views are missing ---

    @Test
    void tiktokUsesViewsWhenPresent() {
        Optional<BigDecimal> rate = tiktok.calculate(
                stats(Platform.TIKTOK, 210000, 95000L, 12000L, 800L));
        assertEquals(new BigDecimal("13.47"), rate.orElseThrow());
    }

    @Test
    void tiktokFallsBackToFollowersWithoutViews() {
        Optional<BigDecimal> rate = tiktok.calculate(
                stats(Platform.TIKTOK, 100000, null, 4000L, 1000L));
        assertEquals(new BigDecimal("5.00"), rate.orElseThrow());
    }

    // --- shared semantics ---

    @Test
    void missingInteractionDataIsUnknownNotZero() {
        // No likes AND no comments recorded: "unknown", never a misleading 0%.
        assertTrue(instagram.calculate(stats(Platform.INSTAGRAM, 84000, null, null, null)).isEmpty());
        assertTrue(youtube.calculate(stats(Platform.YOUTUBE, 1000, 500L, null, null)).isEmpty());
    }

    @Test
    void partialInteractionDataCountsMissingPartAsZero() {
        Optional<BigDecimal> rate = instagram.calculate(
                stats(Platform.INSTAGRAM, 1000, null, 50L, null));
        assertEquals(new BigDecimal("5.00"), rate.orElseThrow());
    }

    // --- registry (Open/Closed wiring) ---

    @Test
    void registryResolvesEveryPlatform() {
        EngagementCalculatorRegistry registry =
                new EngagementCalculatorRegistry(List.of(instagram, youtube, tiktok));
        for (Platform platform : Platform.values()) {
            assertEquals(platform, registry.forPlatform(platform).platform());
        }
    }

    @Test
    void registryRejectsUnregisteredPlatform() {
        EngagementCalculatorRegistry registry =
                new EngagementCalculatorRegistry(List.of(instagram, youtube));
        assertThrows(UnsupportedPlatformException.class,
                () -> registry.forPlatform(Platform.TIKTOK));
    }

    @Test
    void registryFailsFastOnDuplicateStrategies() {
        assertThrows(IllegalStateException.class,
                () -> new EngagementCalculatorRegistry(List.of(instagram, new InstagramEngagementCalculator())));
    }
}
