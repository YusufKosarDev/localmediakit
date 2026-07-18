package com.localmediakit.stats.engagement;

import com.localmediakit.stats.Platform;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves the strategy for a platform. Spring injects every
 * {@link EngagementCalculator} bean, so registering a new platform is just
 * writing a new component — no registry edits. Two calculators claiming the
 * same platform fail fast at startup (toUnmodifiableMap rejects duplicates).
 */
@Component
public class EngagementCalculatorRegistry {

    private final Map<Platform, EngagementCalculator> byPlatform;

    public EngagementCalculatorRegistry(List<EngagementCalculator> calculators) {
        this.byPlatform = calculators.stream()
                .collect(Collectors.toUnmodifiableMap(EngagementCalculator::platform, c -> c));
    }

    public EngagementCalculator forPlatform(Platform platform) {
        EngagementCalculator calculator = byPlatform.get(platform);
        if (calculator == null) {
            throw new UnsupportedPlatformException(platform);
        }
        return calculator;
    }
}
