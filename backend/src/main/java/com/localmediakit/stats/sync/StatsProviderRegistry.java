package com.localmediakit.stats.sync;

import com.localmediakit.stats.Platform;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the provider for a platform, considering only AVAILABLE ones —
 * an unconfigured provider (no API key) is indistinguishable from a platform
 * that has no provider at all. Iterates the small bean list on demand so
 * availability is always evaluated live (keys can differ per environment).
 */
@Component
public class StatsProviderRegistry {

    private final List<StatsProvider> providers;

    public StatsProviderRegistry(List<StatsProvider> providers) {
        this.providers = providers;
    }

    public Optional<StatsProvider> forPlatform(Platform platform) {
        return providers.stream()
                .filter(p -> p.platform() == platform && p.available())
                .findFirst();
    }

    /** Platforms the frontend may offer a "connect" UI for. */
    public List<Platform> availablePlatforms() {
        return providers.stream()
                .filter(StatsProvider::available)
                .map(StatsProvider::platform)
                .distinct()
                .sorted()
                .toList();
    }
}
