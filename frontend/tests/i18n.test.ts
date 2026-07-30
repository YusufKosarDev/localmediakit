import { describe, it, expect } from "vitest";
import {
  DEFAULT_LOCALE, LOCALES, formatCompact, formatDate, formatNumber,
  intlTag, isLocale, normalizeLocale, translate, translator, type Dict,
} from "@/app/_i18n";
import { publicDict } from "@/app/_i18n/public";

describe("locale handling", () => {
  it("accepts only the locales the product ships", () => {
    expect(isLocale("tr")).toBe(true);
    expect(isLocale("en")).toBe(true);
    expect(isLocale("de")).toBe(false);
    expect(isLocale(null)).toBe(false);
  });

  it("falls back to the default rather than rendering nothing", () => {
    // Snapshots published before i18n carry no language at all.
    expect(normalizeLocale(undefined)).toBe(DEFAULT_LOCALE);
    expect(normalizeLocale(null)).toBe(DEFAULT_LOCALE);
    expect(normalizeLocale("klingon")).toBe(DEFAULT_LOCALE);
    expect(normalizeLocale("en")).toBe("en");
  });
});

describe("dictionaries", () => {
  it("keeps every locale complete, so no surface can half-translate", () => {
    const trKeys = Object.keys(publicDict.tr).sort();
    for (const locale of LOCALES) {
      expect(Object.keys(publicDict[locale]).sort(), `${locale} keys`).toEqual(trKeys);
    }
  });

  it("has no empty strings hiding as translations", () => {
    for (const locale of LOCALES) {
      for (const [key, value] of Object.entries(publicDict[locale])) {
        expect(value.trim(), `${locale}.${key}`).not.toBe("");
      }
    }
  });

  /** A gap should read as untranslated copy, never as a raw key. */
  it("falls back to the default language when a key is missing", () => {
    const partial = {
      tr: { greeting: "Merhaba", farewell: "Gule gule" },
      en: { greeting: "Hello" },
    } as unknown as Dict<{ greeting: string; farewell: string }>;

    expect(translate(partial, "en", "greeting")).toBe("Hello");
    expect(translate(partial, "en", "farewell")).toBe("Gule gule");
  });

  it("substitutes placeholders and leaves unknown ones alone", () => {
    const t = translator(publicDict, "en");
    expect(t("publishedOn", { date: "3 May 2026" })).toBe("Published on 3 May 2026");
    // A missing variable must not blank out the sentence.
    expect(t("publishedOn")).toContain("{date}");
  });

  it("translates the public page's section headings", () => {
    expect(translator(publicDict, "tr")("sectionPlatforms")).toBe("Platformlar");
    expect(translator(publicDict, "en")("sectionPlatforms")).toBe("Platforms");
  });
});

/**
 * Number and date formats are the part of i18n that silently produces wrong
 * data rather than merely untranslated text: 6,47 read as English is 647.
 */
describe("locale-aware formatting", () => {
  it("maps each locale to its Intl tag", () => {
    expect(intlTag("tr")).toBe("tr-TR");
    expect(intlTag("en")).toBe("en-US");
  });

  it("swaps the thousands and decimal separators", () => {
    expect(formatNumber(1234.5, "tr")).toBe("1.234,5");
    expect(formatNumber(1234.5, "en")).toBe("1,234.5");
  });

  it("formats compact follower counts per locale", () => {
    // Turkish uses "B" (bin) where English uses "K".
    expect(formatCompact(102000, "tr")).toMatch(/102/);
    expect(formatCompact(102000, "en")).toBe("102K");
  });

  it("writes dates in each language", () => {
    const date = "2026-05-03T10:00:00Z";
    expect(formatDate(date, "tr")).toContain("2026");
    expect(formatDate(date, "en")).toContain("2026");
    expect(formatDate(date, "tr")).not.toBe(formatDate(date, "en"));
  });
});
