"use client";

import { useState } from "react";
import { CalendarClock, Lock, Unlock } from "lucide-react";
import { Button, Input, Label, Select } from "@/app/_components/ui";
import { formatDateTime, type Locale } from "@/app/_i18n";
import { del, put } from "../_lib/api";
import { accents, LANGUAGES, layouts, type Feedback, type Kit, type Translate } from "../_lib/types";

/**
 * The kit's own fields, plus password protection.
 *
 * <p>Unlike the other panels this one edits state the shell owns: the kit row
 * is already in the list, and typing here has to keep the visible list in
 * step. So it takes the kit plus an `onField` setter rather than fetching
 * anything of its own.
 */
export function EditPanel({
  kit,
  feedback,
  t,
  locale,
  onField,
  onSaved,
}: {
  kit: Kit;
  feedback: Feedback;
  t: Translate;
  locale: Locale;
  onField: (field: keyof Kit, value: string | boolean) => void;
  onSaved: () => Promise<void> | void;
}) {
  const [when, setWhen] = useState("");

  async function armSchedule() {
    feedback.clear();
    // datetime-local has no timezone; the Date constructor reads it in the
    // creator's, which is the one they meant.
    const result = await put(
      `/api/mediakits/${kit.id}/schedule`,
      { publishAt: new Date(when).toISOString() },
      t("failedSchedule")
    );
    if (result.ok) {
      setWhen("");
      await onSaved();
      feedback.notify(t("scheduleDone"));
    } else {
      feedback.fail(result.message);
    }
  }

  async function cancelSchedule() {
    feedback.clear();
    const result = await del(`/api/mediakits/${kit.id}/schedule`, t("failedSchedule"), 200);
    if (result.ok) {
      await onSaved();
      feedback.notify(t("scheduleCancelled"));
    } else {
      feedback.fail(result.message);
    }
  }

  async function save() {
    feedback.clear();
    const result = await put(`/api/mediakits/${kit.id}`, {
      title: kit.title,
      headline: kit.headline,
      avatarUrl: kit.avatarUrl,
      theme: kit.theme,
      accent: kit.accent,
      layout: kit.layout,
      language: kit.language,
      slug: kit.slug,
      contactEnabled: kit.contactEnabled,
    });
    if (result.ok) {
      feedback.notify(t("saved"));
      await onSaved();
    } else {
      feedback.fail(result.message);
    }
  }

  async function setPassword() {
    feedback.clear();
    const password = window.prompt(t("passwordPrompt"));
    if (password == null) return;
    const result = await put(`/api/mediakits/${kit.id}/password`, { password }, t("failedSave"), 204);
    if (result.ok) {
      await onSaved();
      feedback.notify(t("passwordSaved"));
    } else {
      feedback.fail(result.message);
    }
  }

  async function removePassword() {
    feedback.clear();
    const result = await del(`/api/mediakits/${kit.id}/password`, t("failedSave"));
    if (result.ok) {
      await onSaved();
      feedback.notify(t("passwordRemoved"));
    } else {
      feedback.fail(result.message);
    }
  }

  return (
    <div className="grid max-w-xl gap-3">
      <div className="grid gap-1.5">
        <Label htmlFor={`kit-title-${kit.id}`}>{t("fieldTitle")}</Label>
        <Input id={`kit-title-${kit.id}`} value={kit.title} onChange={(e) => onField("title", e.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor={`kit-headline-${kit.id}`}>{t("fieldHeadline")}</Label>
        <Input id={`kit-headline-${kit.id}`} value={kit.headline ?? ""} onChange={(e) => onField("headline", e.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor={`kit-avatarUrl-${kit.id}`}>{t("fieldAvatarUrl")}</Label>
        <Input id={`kit-avatarUrl-${kit.id}`} value={kit.avatarUrl ?? ""} onChange={(e) => onField("avatarUrl", e.target.value)} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor={`kit-theme-${kit.id}`}>{t("dashboardTheme")}</Label>
          <Select id={`kit-theme-${kit.id}`} value={kit.theme} onChange={(e) => onField("theme", e.target.value)}>
            <option value="light">{t("themeLight")}</option>
            <option value="dark">{t("themeDark")}</option>
          </Select>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`kit-slug-${kit.id}`}>{t("fieldSlug")}</Label>
          <Input id={`kit-slug-${kit.id}`} value={kit.slug} onChange={(e) => onField("slug", e.target.value)} />
        </div>
      </div>

      {/* Appearance. A fixed palette rather than a colour input: every accent
          here is contrast-checked against the surfaces it renders on, so no
          selection can produce an unreadable public page. */}
      <fieldset className="grid gap-1.5">
        <legend className="text-sm font-medium text-fg">{t("accentLegend")}</legend>
        <div className="mt-1 flex flex-wrap gap-2">
          {accents(t).map((a) => {
            const selected = (kit.accent || "violet") === a.id;
            return (
              <button
                key={a.id}
                type="button"
                onClick={() => onField("accent", a.id)}
                aria-pressed={selected}
                title={a.label}
                className={`flex items-center gap-2 rounded-xl border px-2.5 py-1.5 text-sm transition-colors ${
                  selected ? "border-brand bg-brand-weak text-brand" : "border-line text-muted hover:bg-page"
                }`}
              >
                <span
                  aria-hidden="true"
                  className="h-4 w-4 shrink-0 rounded-full ring-1 ring-line"
                  style={{ background: a.swatch }}
                />
                {a.label}
              </button>
            );
          })}
        </div>
      </fieldset>

      <fieldset className="grid gap-1.5">
        <legend className="text-sm font-medium text-fg">{t("languageLegend")}</legend>
        <div className="mt-1 flex flex-wrap gap-2">
          {LANGUAGES.map((l) => {
            const selected = (kit.language || "tr") === l.id;
            return (
              <button
                key={l.id}
                type="button"
                onClick={() => onField("language", l.id)}
                aria-pressed={selected}
                className={`rounded-xl border px-3 py-1.5 text-sm transition-colors ${
                  selected ? "border-brand bg-brand-weak text-brand" : "border-line text-muted hover:bg-page"
                }`}
              >
                {l.label}
              </button>
            );
          })}
        </div>
        <p className="text-xs text-faint">
          {t("languageHint")}
        </p>
      </fieldset>

      <fieldset className="grid gap-1.5">
        <legend className="text-sm font-medium text-fg">{t("layoutLegend")}</legend>
        <div className="mt-1 flex flex-wrap gap-2">
          {layouts(t).map((l) => {
            const selected = (kit.layout || "classic") === l.id;
            return (
              <button
                key={l.id}
                type="button"
                onClick={() => onField("layout", l.id)}
                aria-pressed={selected}
                className={`rounded-xl border px-3 py-1.5 text-left text-sm transition-colors ${
                  selected ? "border-brand bg-brand-weak text-brand" : "border-line text-muted hover:bg-page"
                }`}
              >
                <span className="block font-medium">{l.label}</span>
                <span className="block text-xs opacity-80">{l.hint}</span>
              </button>
            );
          })}
        </div>
      </fieldset>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={kit.contactEnabled}
          onChange={(e) => onField("contactEnabled", e.target.checked)}
          className="h-4 w-4 accent-[--brand-strong]"
        />
        {t("contactToggle")}
        <span className="text-xs text-faint">
          {t("contactToggleHint")}
        </span>
      </label>
      <div className="flex flex-wrap gap-2">
        <Button onClick={save}>{t("save")}</Button>
        {kit.passwordProtected ? (
          <Button variant="secondary" onClick={removePassword}>
            <Unlock className="h-4 w-4" /> {t("removePassword")}
          </Button>
        ) : (
          <Button variant="secondary" onClick={setPassword}>
            <Lock className="h-4 w-4" /> {t("setPassword")}
          </Button>
        )}
      </div>
      <p className="text-xs text-faint">{t("publishNote")}</p>

      {/* Scheduled publish. Sits under the publish note because it is the same
          decision moved in time, and reads better right after the sentence
          explaining that nothing goes out until you publish. */}
      <fieldset className="rounded-lg border border-line p-3">
        <legend className="flex items-center gap-1.5 px-1 text-sm font-medium">
          <CalendarClock className="h-3.5 w-3.5 text-brand" /> {t("scheduleTitle")}
        </legend>
        <p className="text-xs text-muted">{t("scheduleHint")}</p>

        {kit.scheduleError && (
          <p className="mt-2 text-xs text-danger">
            {t("scheduleFailedNotice", { reason: kit.scheduleError })}
          </p>
        )}

        {kit.scheduledPublishAt ? (
          <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
            <span>{t("scheduleArmed", { when: formatDateTime(kit.scheduledPublishAt, locale) })}</span>
            <Button size="sm" variant="ghost" onClick={cancelSchedule}>
              {t("scheduleCancel")}
            </Button>
          </div>
        ) : (
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {/* datetime-local reads and writes the creator's own clock; the
                value is converted to an instant before it is sent, so the
                server never has to guess which timezone a wall clock meant. */}
            <Input
              type="datetime-local"
              className="w-56"
              value={when}
              onChange={(e) => setWhen(e.target.value)}
            />
            <Button size="sm" disabled={!when} onClick={armSchedule}>
              {t("scheduleSet")}
            </Button>
          </div>
        )}
      </fieldset>
    </div>
  );
}
