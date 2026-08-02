package com.localmediakit.mediakit;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The closed list of looks, tested as the accessibility guarantee it is.
 *
 * <p>The frontend verifies each accent's contrast against the surfaces it is
 * drawn on. That only means anything while this list is the only way a value
 * reaches the database: a normalizer that let an unknown accent through would
 * put an unverified colour on a public page, and the contrast test on the other
 * side would still pass because it checks the list, not the data.
 */
class KitAppearanceTest {

    @Test
    void everyCuratedValueIsAccepted() {
        for (String accent : KitAppearance.ACCENTS) {
            assertThat(KitAppearance.normalizeAccent(accent)).isEqualTo(accent);
        }
        for (String theme : KitAppearance.THEMES) {
            assertThat(KitAppearance.normalizeTheme(theme)).isEqualTo(theme);
        }
        for (String layout : KitAppearance.LAYOUTS) {
            assertThat(KitAppearance.normalizeLayout(layout)).isEqualTo(layout);
        }
    }

    @Test
    void anythingOutsideTheListIsRefusedRatherThanDefaulted() {
        // Silently defaulting would publish a look the owner did not choose and
        // tell them it worked.
        assertThatThrownBy(() -> KitAppearance.normalizeAccent("#ff00ff"))
                .isInstanceOf(InvalidAppearanceException.class);
        assertThatThrownBy(() -> KitAppearance.normalizeTheme("sepia"))
                .isInstanceOf(InvalidAppearanceException.class);
        assertThatThrownBy(() -> KitAppearance.normalizeLayout("masonry"))
                .isInstanceOf(InvalidAppearanceException.class);
    }

    @Test
    void absentMeansTheDefault() {
        assertThat(KitAppearance.normalizeAccent(null)).isEqualTo(KitAppearance.DEFAULT_ACCENT);
        assertThat(KitAppearance.normalizeAccent("   ")).isEqualTo(KitAppearance.DEFAULT_ACCENT);
        assertThat(KitAppearance.normalizeTheme(null)).isEqualTo(KitAppearance.DEFAULT_THEME);
        assertThat(KitAppearance.normalizeLayout(null)).isEqualTo(KitAppearance.DEFAULT_LAYOUT);
    }

    @Test
    void caseAndPaddingAreForgivenButTheValueStillHasToBeReal() {
        assertThat(KitAppearance.normalizeAccent("  OCEAN ")).isEqualTo("ocean");
        assertThatThrownBy(() -> KitAppearance.normalizeAccent("  OCEANIC "))
                .isInstanceOf(InvalidAppearanceException.class);
    }

    @Test
    void foldingDoesNotDependOnTheServersLocale() {
        // The reason these are strings and not an enum. On a Turkish JVM the
        // default-locale fold turns "I" into a dotless "i", so a value entered
        // in capitals would stop matching its own list entry.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertThat(KitAppearance.normalizeTheme("LIGHT")).isEqualTo("light");
            assertThat(KitAppearance.normalizeLayout("CLASSIC")).isEqualTo("classic");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void theDefaultsAreThemselvesInTheList() {
        // Guards the arrangement rather than the code: a default that is not an
        // allowed value would be written on create and rejected on the next edit.
        assertThat(KitAppearance.ACCENTS).contains(KitAppearance.DEFAULT_ACCENT);
        assertThat(KitAppearance.THEMES).contains(KitAppearance.DEFAULT_THEME);
        assertThat(KitAppearance.LAYOUTS).contains(KitAppearance.DEFAULT_LAYOUT);
    }
}
