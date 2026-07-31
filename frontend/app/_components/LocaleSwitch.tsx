"use client";

import { Languages } from "lucide-react";
import { LOCALES, type Locale } from "@/app/_i18n";

/**
 * Language toggle for signed-out surfaces.
 *
 * <p>Signed-in users do not get this: their language is an account setting, so
 * offering a second, device-local switch would give them two answers to the
 * same question.
 */
export function LocaleSwitch({
  locale,
  onChange,
  label,
}: {
  locale: Locale;
  onChange: (next: Locale) => void;
  label: string;
}) {
  return (
    <div className="flex items-center gap-1" role="group" aria-label={label}>
      <Languages aria-hidden="true" className="mr-1 h-4 w-4 text-faint" />
      {LOCALES.map((code) => (
        <button
          key={code}
          type="button"
          onClick={() => onChange(code)}
          aria-pressed={locale === code}
          className={`rounded-lg px-2 py-1 text-xs font-medium uppercase transition-colors ${
            locale === code ? "bg-brand-weak text-brand" : "text-muted hover:text-fg"
          }`}
        >
          {code}
        </button>
      ))}
    </div>
  );
}
