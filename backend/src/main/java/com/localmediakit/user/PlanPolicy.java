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

    /** FREE publishes carry the LocalMediaKit badge on the public page. */
    public boolean showsBranding(Plan plan) {
        return plan != Plan.PRO;
    }

    /** Password-protecting a kit is a PRO feature. */
    public boolean passwordProtectionEnabled(Plan plan) {
        return plan == Plan.PRO;
    }

    /** FREE sees only the most recent versions; PRO sees the full history. */
    public int maxVisibleVersions(Plan plan) {
        return plan == Plan.PRO ? Integer.MAX_VALUE : 2;
    }

    public void assertPasswordProtectionAllowed(Plan plan) {
        if (!passwordProtectionEnabled(plan)) {
            throw new PlanLimitExceededException(
                    "Password protection is a PRO feature. Upgrade to protect this kit.");
        }
    }

    /**
     * Downgrade rule: existing kits and their live pages survive a PRO->FREE
     * drop (brand links must not break), but only the OLDEST kits within the
     * plan limit may be (re)published. olderKitCount is how many of the
     * owner's kits were created before the one being published.
     */
    public void assertCanPublish(Plan plan, long olderKitCount) {
        if (olderKitCount >= maxMediaKits(plan)) {
            throw new PlanLimitExceededException(
                    "Your plan allows publishing only the first " + maxMediaKits(plan)
                            + " media kit(s). Upgrade to PRO to publish this one.");
        }
    }

    public void assertCanCreateMediaKit(Plan plan, long currentCount) {
        int max = maxMediaKits(plan);
        if (currentCount >= max) {
            throw new PlanLimitExceededException(
                    "Your plan allows up to " + max + " media kit(s). Upgrade to PRO for more.");
        }
    }
}
