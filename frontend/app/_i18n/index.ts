/**
 * Translation without a library.
 *
 * <p>next-intl and friends assume one locale for the whole app, usually
 * carried in the URL by middleware. Neither assumption holds here: the public
 * media-kit page takes its language from the published snapshot (so one
 * visitor can open a Turkish kit and an English one back to back), and putting
 * a locale segment in URLs would rewrite every published link. A lookup
 * function models "this page has this language" directly, and costs nothing at
 * runtime beyond the strings themselves.
 *
 * <p>Dictionaries are split per surface so the public page ships only its own
 * handful of strings — its First Load budget is the tightest in the project.
 */

export const LOCALES = ["tr", "en"] as const;
export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "tr";

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && (LOCALES as readonly string[]).includes(value);
}

/** Anything unknown becomes the default rather than rendering blank UI. */
export function normalizeLocale(value: unknown): Locale {
  return isLocale(value) ? value : DEFAULT_LOCALE;
}

/** A dictionary is the Turkish shape; English must supply the same keys. */
export type Dict<T extends Record<string, string>> = Record<Locale, T>;

/**
 * Looks up one string.
 *
 * <p>A missing translation falls back to Turkish rather than to the key, so a
 * gap shows as untranslated copy instead of `dashboard.kits.empty` leaking
 * into the interface. The type system already requires both locales to be
 * complete; this is the runtime backstop.
 */
export function translate<T extends Record<string, string>>(
  dict: Dict<T>,
  locale: Locale,
  key: keyof T
): string {
  const table = dict[locale] ?? dict[DEFAULT_LOCALE];
  return table[key] ?? dict[DEFAULT_LOCALE][key] ?? String(key);
}

/** Binds a dictionary and locale so components call t("key"). */
export function translator<T extends Record<string, string>>(dict: Dict<T>, locale: Locale) {
  return (key: keyof T, vars?: Record<string, string | number>) => {
    const raw = translate(dict, locale, key);
    if (!vars) return raw;
    // {name}-style placeholders; deliberately minimal — no plural rules, no
    // date syntax, because nothing in this product needs them.
    return raw.replace(/\{(\w+)\}/g, (match, name) =>
      name in vars ? String(vars[name]) : match
    );
  };
}

/* ------------------------------------------------------------------ */
/* Locale-aware formatting                                             */
/* ------------------------------------------------------------------ */

/**
 * Intl tags for each locale. Turkish writes 6,47 and 1.234; English writes
 * 6.47 and 1,234 — the same engagement rate reads as a thousand-fold
 * difference if the tag is wrong, so these are never hardcoded at call sites.
 */
const INTL_TAG: Record<Locale, string> = { tr: "tr-TR", en: "en-US" };

export function intlTag(locale: Locale): string {
  return INTL_TAG[locale] ?? INTL_TAG[DEFAULT_LOCALE];
}

export function formatNumber(value: number, locale: Locale): string {
  return value.toLocaleString(intlTag(locale));
}

export function formatCompact(value: number, locale: Locale): string {
  return new Intl.NumberFormat(intlTag(locale), {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

export function formatDateTime(value: string | Date, locale: Locale): string {
  return new Date(value).toLocaleString(intlTag(locale));
}

export function formatDate(value: string | Date, locale: Locale): string {
  return new Date(value).toLocaleDateString(intlTag(locale), {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}
