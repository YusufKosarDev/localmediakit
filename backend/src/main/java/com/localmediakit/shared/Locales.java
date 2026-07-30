package com.localmediakit.shared;

import java.util.List;
import java.util.Locale;

/**
 * The interface languages the product ships.
 *
 * <p>Plain lowercase strings rather than a Java enum, matching the appearance
 * fields: an {@code @Enumerated} column and a SQL DEFAULT already drifted
 * apart over letter case once, and {@code toUpperCase()} is locale-sensitive
 * (a Turkish JVM maps "i" to a dotted capital).
 */
public final class Locales {

    public static final String DEFAULT = "tr";
    public static final List<String> SUPPORTED = List.of("tr", "en");

    private Locales() {
    }

    /**
     * Blank falls back to the default; an unknown value is rejected.
     *
     * <p>Rejecting rather than silently defaulting keeps a typo from quietly
     * publishing a kit in the wrong language. Readers stay tolerant
     * separately, so an unexpected stored value still renders something.
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String candidate = value.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED.contains(candidate)) {
            throw new UnsupportedLocaleException(
                    "Desteklenmeyen dil: " + value + ". Izin verilenler: " + String.join(", ", SUPPORTED));
        }
        return candidate;
    }

    /** Never throws — for rendering paths that must not fail on old data. */
    public static String orDefault(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String candidate = value.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED.contains(candidate) ? candidate : DEFAULT;
    }
}
