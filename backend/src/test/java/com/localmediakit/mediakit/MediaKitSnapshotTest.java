package com.localmediakit.mediakit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules that keep already-published pages rendering the way they were
 * published.
 *
 * <p>Every field added to the snapshot since the first release is nullable, and
 * these accessors are what stops that from mattering: a page frozen before
 * accents existed still renders the original violet, one frozen before the
 * contact form existed still has no form. The guarantee the whole product rests
 * on -- a published page does not change until its owner republishes -- lives in
 * these few methods.
 *
 * <p>Covered here rather than only through the publish flow because that path
 * only ever produces snapshots from the current schema. Nothing in the suite was
 * exercising what happens to an older one except by accident, and the classes
 * that boot Spring cannot take part in mutation testing.
 */
class MediaKitSnapshotTest {

    /** A snapshot from the first schema generation: everything optional is absent. */
    private MediaKitSnapshot oldest() {
        return new MediaKitSnapshot(
                "eski-kit", "Eski Kit", null, null, "light",
                null, null, null, "Uretici",
                null, null, null, null, null, null, null);
    }

    @Test
    void anOlderSnapshotRendersTheOriginalLook() {
        assertThat(oldest().accentOrDefault()).isEqualTo(KitAppearance.DEFAULT_ACCENT);
        assertThat(oldest().layoutOrDefault()).isEqualTo(KitAppearance.DEFAULT_LAYOUT);
    }

    @Test
    void aStoredLookIsKeptAsPublished() {
        MediaKitSnapshot styled = new MediaKitSnapshot(
                "kit", "Kit", null, null, "dark",
                "ocean", "panel", "en", "Uretici",
                null, null, null, null, null, null, null);

        assertThat(styled.accentOrDefault()).isEqualTo("ocean");
        assertThat(styled.layoutOrDefault()).isEqualTo("panel");
        assertThat(styled.languageOrDefault()).isEqualTo("en");
    }

    @Test
    void aBlankLookIsTreatedAsAbsentRatherThanAsAValue() {
        // An empty string reaching the renderer would produce an unstyled page,
        // which is worse than the default it was meant to stand in for.
        MediaKitSnapshot blank = new MediaKitSnapshot(
                "kit", "Kit", null, null, "light",
                "  ", "", null, "Uretici",
                null, null, null, null, null, null, null);

        assertThat(blank.accentOrDefault()).isEqualTo(KitAppearance.DEFAULT_ACCENT);
        assertThat(blank.layoutOrDefault()).isEqualTo(KitAppearance.DEFAULT_LAYOUT);
    }

    @Test
    void snapshotsPredatingI18nStayTurkish() {
        assertThat(oldest().languageOrDefault()).isEqualTo("tr");
    }

    @Test
    void aPagePublishedBeforeTheShowcaseExistedSimplyHasNone() {
        // The newest nullable list, and the one most likely to be got wrong:
        // every page published before this feature has null here, and a null
        // reaching the renderer is a 500 on a page nobody touched.
        assertThat(oldest().mediaOrEmpty()).isEmpty();

        MediaKitSnapshot withMedia = new MediaKitSnapshot(
                "kit", "Kit", null, null, "light", null, null, null, "Uretici",
                null, null, null, null, null, null,
                List.of(new MediaKitSnapshot.MediaSnapshot(
                        "En iyi video", "https://example.com/v", null, "YOUTUBE", null)));

        assertThat(withMedia.mediaOrEmpty()).hasSize(1);
        assertThat(withMedia.mediaOrEmpty().get(0).title()).isEqualTo("En iyi video");
    }

    @Test
    void absentListsReadAsEmptyRatherThanNull() {
        // The renderer iterates these; a null would be a 500 on a page that is
        // supposed to keep working precisely because nobody has touched it.
        assertThat(oldest().platformsOrEmpty()).isEmpty();
        assertThat(oldest().demographicsOrEmpty()).isEmpty();
        assertThat(oldest().collaborationsOrEmpty()).isEmpty();
        assertThat(oldest().rateCardOrEmpty()).isEmpty();
    }

    @Test
    void presentListsAreReturnedUntouched() {
        // Each list is asserted separately and with content. Checking only the
        // absent case leaves an accessor that always returns empty passing every
        // test -- which would publish a page with its stats, audience and
        // collaborations silently missing.
        MediaKitSnapshot populated = new MediaKitSnapshot(
                "kit", "Kit", null, null, "light", null, null, null, "Uretici",
                List.of(new MediaKitSnapshot.PlatformStatSnapshot(
                        "YOUTUBE", 1000L, 500L, null, null, null, null)),
                List.of(new MediaKitSnapshot.DemographicSnapshot("AGE", "25-34", null)),
                List.of(new MediaKitSnapshot.CollaborationSnapshot(
                        "Marka", null, null, null, null)),
                null,
                List.of(new MediaKitSnapshot.RateCardSnapshot("Reels", null, "TRY", null)),
                null, null);

        assertThat(populated.platformsOrEmpty()).hasSize(1);
        assertThat(populated.platformsOrEmpty().get(0).platform()).isEqualTo("YOUTUBE");
        assertThat(populated.demographicsOrEmpty()).hasSize(1);
        assertThat(populated.demographicsOrEmpty().get(0).label()).isEqualTo("25-34");
        assertThat(populated.collaborationsOrEmpty()).hasSize(1);
        assertThat(populated.collaborationsOrEmpty().get(0).brandName()).isEqualTo("Marka");
        assertThat(populated.rateCardOrEmpty()).hasSize(1);
        assertThat(populated.rateCardOrEmpty().get(0).serviceName()).isEqualTo("Reels");
    }

    @Test
    void theBadgeDefaultsToShownOnSnapshotsThatPredateIt() {
        // The badge was a FREE-tier marker, so a snapshot with no opinion has to
        // keep showing it rather than silently upgrading an old page's look.
        assertThat(oldest().showBadgeOrDefault()).isTrue();

        MediaKitSnapshot hidden = new MediaKitSnapshot(
                "kit", "Kit", null, null, "light", null, null, null, "Uretici",
                null, null, null, false, null, null, null);
        assertThat(hidden.showBadgeOrDefault()).isFalse();
    }

    @Test
    void theContactFormDefaultsToAbsentOnSnapshotsThatPredateIt() {
        // The opposite default, and deliberately so: a page published before the
        // feature existed must not start collecting messages on its own.
        assertThat(oldest().contactEnabledOrDefault()).isFalse();

        MediaKitSnapshot withForm = new MediaKitSnapshot(
                "kit", "Kit", null, null, "light", null, null, null, "Uretici",
                null, null, null, null, null, true, null);
        assertThat(withForm.contactEnabledOrDefault()).isTrue();
    }
}
