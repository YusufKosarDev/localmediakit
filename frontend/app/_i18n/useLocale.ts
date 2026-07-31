"use client";

import { useEffect, useState } from "react";
import { DEFAULT_LOCALE, normalizeLocale, type Locale } from "./index";

/** Where an anonymous visitor's choice is remembered. */
export const LOCALE_STORAGE_KEY = "lmk.locale";

/**
 * The interface language for a signed-out surface (landing, login, register).
 *
 * <p>The server renders these statically in the default language, which is
 * also what search engines index. Reading a stored preference has to happen on
 * the client, so a visitor who has switched before sees one paint of Turkish
 * first. That is the price of these pages staying static — the alternative is
 * middleware or headers(), which would make them dynamic and cost the edge
 * cache the whole project is built around.
 */
export function useStoredLocale(): [Locale, (next: Locale) => void] {
  const [locale, setLocale] = useState<Locale>(DEFAULT_LOCALE);

  useEffect(() => {
    const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    if (stored) setLocale(normalizeLocale(stored));
  }, []);

  function choose(next: Locale) {
    setLocale(next);
    window.localStorage.setItem(LOCALE_STORAGE_KEY, next);
    // Keep the document in step for assistive tech on these surfaces; the
    // published kit page sets its own on its wrapper instead.
    document.documentElement.lang = next;
  }

  return [locale, choose];
}

/**
 * Mirrors the signed-in user's account locale into localStorage.
 *
 * <p>So that signing out, or landing on the marketing page later, keeps the
 * language they already chose rather than snapping back to Turkish.
 */
export function rememberLocale(locale: string) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(LOCALE_STORAGE_KEY, normalizeLocale(locale));
  document.documentElement.lang = normalizeLocale(locale);
}
