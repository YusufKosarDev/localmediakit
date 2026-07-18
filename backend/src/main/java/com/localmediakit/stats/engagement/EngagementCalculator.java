package com.localmediakit.stats.engagement;

import com.localmediakit.stats.Platform;
import com.localmediakit.stats.PlatformStats;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Strategy: each platform computes its engagement rate with its own formula.
 * Adding a platform means adding one new implementation (a Spring component);
 * nothing else changes — the {@link EngagementCalculatorRegistry} discovers
 * implementations automatically (Open/Closed).
 */
public interface EngagementCalculator {

    /** The platform this strategy is responsible for. */
    Platform platform();

    /**
     * The engagement rate in percent, or empty when it cannot be computed
     * (missing interactions or a zero/absent denominator). Empty is deliberate:
     * a misleading "0%" would look like measured-and-bad rather than unknown.
     */
    Optional<BigDecimal> calculate(PlatformStats stats);
}
