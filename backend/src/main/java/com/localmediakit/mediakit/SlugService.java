package com.localmediakit.mediakit;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Slug domain logic: turns free text into a URL-safe slug, blocks reserved
 * words, and resolves collisions by appending a numeric suffix.
 */
@Service
public class SlugService {

    private static final int MAX_LENGTH = 60;
    private static final String FALLBACK = "kit";

    private static final Set<String> RESERVED = Set.of(
            "app", "api", "admin", "login", "register", "dashboard", "me", "public",
            "demo", "preview", "static", "assets", "_next", "favicon.ico", "robots.txt",
            "sitemap.xml");

    /** Normalizes text to a URL-safe slug (lowercase, hyphenated, ASCII). */
    public String slugify(String input) {
        if (input == null) {
            return "";
        }
        String s = input.trim()
                // Drop apostrophes so possessive suffixes stay attached (Yusuf'un -> yusufun)
                .replace("'", "").replace("’", "")
                // Turkish-specific letters (both cases) before ASCII folding
                .replace("İ", "i").replace("I", "i").replace("ı", "i")
                .replace("Ğ", "g").replace("ğ", "g")
                .replace("Ü", "u").replace("ü", "u")
                .replace("Ş", "s").replace("ş", "s")
                .replace("Ö", "o").replace("ö", "o")
                .replace("Ç", "c").replace("ç", "c")
                .toLowerCase(Locale.ROOT);

        // Strip remaining accents/diacritics
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        // Everything that is not a-z0-9 becomes a hyphen; collapse and trim.
        s = s.replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");

        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return s;
    }

    public boolean isReserved(String slug) {
        return RESERVED.contains(slug);
    }

    /**
     * Returns a slug based on {@code base} that is neither reserved nor already
     * taken, appending -2, -3, ... on collision.
     *
     * @param isTaken predicate telling whether a candidate is already used
     */
    public String makeUnique(String base, Predicate<String> isTaken) {
        String root = base.isEmpty() ? FALLBACK : base;
        String candidate = root;
        int suffix = 2;
        while (isReserved(candidate) || isTaken.test(candidate)) {
            candidate = root + "-" + suffix;
            suffix++;
        }
        return candidate;
    }
}
