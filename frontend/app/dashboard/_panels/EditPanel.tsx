"use client";

import { Lock, Unlock } from "lucide-react";
import { Button, Input, Label, Select } from "@/app/_components/ui";
import { del, put } from "../_lib/api";
import { ACCENTS, LAYOUTS, type Feedback, type Kit } from "../_lib/types";

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
  onField,
  onSaved,
}: {
  kit: Kit;
  feedback: Feedback;
  onField: (field: keyof Kit, value: string | boolean) => void;
  onSaved: () => Promise<void> | void;
}) {
  async function save() {
    feedback.clear();
    const result = await put(`/api/mediakits/${kit.id}`, {
      title: kit.title,
      headline: kit.headline,
      avatarUrl: kit.avatarUrl,
      theme: kit.theme,
      accent: kit.accent,
      layout: kit.layout,
      slug: kit.slug,
      contactEnabled: kit.contactEnabled,
    });
    if (result.ok) {
      feedback.notify("Kaydedildi.");
      await onSaved();
    } else {
      feedback.fail(result.message);
    }
  }

  async function setPassword() {
    feedback.clear();
    const password = window.prompt("Bu kit icin sifre belirleyin (en az 4 karakter):");
    if (password == null) return;
    const result = await put(`/api/mediakits/${kit.id}/password`, { password }, "Sifre belirlenemedi", 204);
    if (result.ok) {
      await onSaved();
      feedback.notify("Sifre kaydedildi. Public sayfaya yansimasi icin Yayinla.");
    } else {
      feedback.fail(result.message);
    }
  }

  async function removePassword() {
    feedback.clear();
    const result = await del(`/api/mediakits/${kit.id}/password`, "Sifre kaldirilamadi");
    if (result.ok) {
      await onSaved();
      feedback.notify("Sifre kaldirildi. Public sayfaya yansimasi icin Yayinla.");
    } else {
      feedback.fail(result.message);
    }
  }

  return (
    <div className="grid max-w-xl gap-3">
      <div className="grid gap-1.5">
        <Label>Baslik</Label>
        <Input value={kit.title} onChange={(e) => onField("title", e.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label>Headline</Label>
        <Input value={kit.headline ?? ""} onChange={(e) => onField("headline", e.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label>Avatar URL</Label>
        <Input value={kit.avatarUrl ?? ""} onChange={(e) => onField("avatarUrl", e.target.value)} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label>Tema</Label>
          <Select value={kit.theme} onChange={(e) => onField("theme", e.target.value)}>
            <option value="light">Acik</option>
            <option value="dark">Koyu</option>
          </Select>
        </div>
        <div className="grid gap-1.5">
          <Label>Slug</Label>
          <Input value={kit.slug} onChange={(e) => onField("slug", e.target.value)} />
        </div>
      </div>

      {/* Appearance. A fixed palette rather than a colour input: every accent
          here is contrast-checked against the surfaces it renders on, so no
          selection can produce an unreadable public page. */}
      <fieldset className="grid gap-1.5">
        <legend className="text-sm font-medium text-fg">Vurgu rengi</legend>
        <div className="mt-1 flex flex-wrap gap-2">
          {ACCENTS.map((a) => {
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
        <legend className="text-sm font-medium text-fg">Duzen</legend>
        <div className="mt-1 flex flex-wrap gap-2">
          {LAYOUTS.map((l) => {
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
        Iletisim formu (marka teklifleri)
        <span className="text-xs text-faint">
          — kapatmak alimi hemen durdurur; formun sayfadan kalkmasi icin Yayinla
        </span>
      </label>
      <div className="flex flex-wrap gap-2">
        <Button onClick={save}>Kaydet</Button>
        {kit.passwordProtected ? (
          <Button variant="secondary" onClick={removePassword}>
            <Unlock className="h-4 w-4" /> Sifreyi kaldir
          </Button>
        ) : (
          <Button variant="secondary" onClick={setPassword}>
            <Lock className="h-4 w-4" /> Sifre koy
          </Button>
        )}
      </div>
      <p className="text-xs text-faint">Not: degisiklikler public sayfaya ancak Yayinla ile yansir.</p>
    </div>
  );
}
