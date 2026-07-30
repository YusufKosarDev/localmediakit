package com.localmediakit.mediakit;

import java.util.List;
import java.util.Locale;

/**
 * The closed set of looks a public page may have.
 *
 * <p>Deliberately a fixed list rather than free input. A colour picker would
 * let someone choose an accent that fails contrast against the page it sits
 * on, and the public page is the one surface that has to keep its
 * accessibility score. Every value here has its contrast verified against the
 * real surfaces (see the palette test on the frontend), so an inaccessible
 * combination is not something a user can express.
 *
 * <p>Values are lowercase strings, matching the existing {@code theme} column.
 * They are not a Java enum on purpose: an enum-backed column and a SQL DEFAULT
 * already drifted apart once over letter case, and {@code toUpperCase()} is
 * locale-sensitive.
 */
public final class KitAppearance {

    public static final String DEFAULT_THEME = "light";
    public static final String DEFAULT_ACCENT = "violet";
    public static final String DEFAULT_LAYOUT = "classic";

    public static final List<String> THEMES = List.of("light", "dark");
    public static final List<String> ACCENTS = List.of("violet", "ocean", "forest", "amber", "rose", "graphite");
    public static final List<String> LAYOUTS = List.of("classic", "panel");

    private KitAppearance() {
    }

    public static String normalizeTheme(String value) {
        return normalize(value, THEMES, DEFAULT_THEME);
    }

    public static String normalizeAccent(String value) {
        return normalize(value, ACCENTS, DEFAULT_ACCENT);
    }

    public static String normalizeLayout(String value) {
        return normalize(value, LAYOUTS, DEFAULT_LAYOUT);
    }

    /**
     * Blank falls back to the default; anything else must be a known value.
     *
     * <p>Rejecting rather than silently defaulting on a wrong value keeps a
     * typo from quietly publishing the wrong look. The renderer stays
     * forgiving separately, so an older or unexpected stored value still shows
     * a sane page instead of an unstyled one.
     */
    private static String normalize(String value, List<String> allowed, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String candidate = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(candidate)) {
            throw new InvalidAppearanceException(
                    "Gecersiz secim: " + value + ". Izin verilenler: " + String.join(", ", allowed));
        }
        return candidate;
    }
}
