package com.localmediakit.user;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Central place for plan-based limits. Today everyone is on FREE; when paid
 * plans are enabled, only this class changes.
 */
@Component
public class PlanPolicy {

    private static final Map<Plan, Integer> MAX_MEDIA_KITS = Map.of(
            Plan.FREE, 1,
            Plan.PRO, Integer.MAX_VALUE);

    public int maxMediaKits(Plan plan) {
        return MAX_MEDIA_KITS.getOrDefault(plan, 1);
    }

    /** FREE sees only the total view counter; PRO gets the full breakdown. */
    public boolean detailedAnalyticsEnabled(Plan plan) {
        return plan == Plan.PRO;
    }

    public void assertCanCreateMediaKit(Plan plan, long currentCount) {
        int max = maxMediaKits(plan);
        if (currentCount >= max) {
            throw new PlanLimitExceededException(
                    "Your plan allows up to " + max + " media kit(s). Upgrade to PRO for more.");
        }
    }
}
