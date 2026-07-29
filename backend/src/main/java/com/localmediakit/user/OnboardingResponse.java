package com.localmediakit.user;

/**
 * Where the signed-in user stands in the getting-started flow.
 *
 * <p>Every step is derived from the account's real data rather than from a
 * stored progress marker, so the guidance can never claim a step is done when
 * the underlying kit, stats or publish is gone.
 *
 * @param dismissed   the user asked not to be shown the guidance again
 * @param hasKit      owns at least one media kit
 * @param hasStats    at least one of those kits has a platform measurement
 * @param hasPublished at least one kit is live on a public URL
 * @param publicSlug  the slug of a live kit, so the UI can link to the result
 */
public record OnboardingResponse(boolean dismissed, boolean hasKit, boolean hasStats,
                                 boolean hasPublished, String publicSlug) {

    /** True once nothing is left to guide the user through. */
    public boolean complete() {
        return hasKit && hasStats && hasPublished;
    }
}
