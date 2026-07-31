"use client";

import { normalizeLocale, translator } from "@/app/_i18n";
import { publicDict } from "@/app/_i18n/public";
import { useState } from "react";
import { Lock } from "lucide-react";
import KitCard, { PublicKit } from "./KitCard";
import { Button, Card, Input } from "@/app/_components/ui";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

// Client-side unlock for a protected kit. The static page ships only this gate
// (no sensitive data reaches the edge); the content is fetched per-request from
// the backend after the password is verified, then rendered with the shared
// KitCard — identical to a public kit once unlocked.
export default function PasswordGate({
  slug,
  title,
  theme,
  accent,
  language,
}: {
  slug: string;
  title: string;
  theme: string;
  accent?: string | null;
  language?: string | null;
}) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const locale = normalizeLocale(language);
  const t = translator(publicDict, locale);
  const [kit, setKit] = useState<PublicKit | null>(null);

  if (kit) return <KitCard kit={kit} />;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/public/kits/${slug}/unlock`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password }),
      });
      if (res.ok) {
        setKit(await res.json());
      } else if (res.status === 401) {
        setError(t("lockedWrong"));
      } else if (res.status === 429) {
        setError(t("lockedTooMany"));
      } else {
        setError(`Acilamadi (HTTP ${res.status}).`);
      }
    } catch {
      setError(t("contactFailed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div lang={locale} data-theme={theme === "dark" ? "dark" : "light"} data-accent={accent ?? "violet"}>
      <main className="grid min-h-screen place-items-center bg-page px-5 text-fg">
        <Card className="w-full max-w-sm p-7 text-center">
          <div className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-brand-weak text-brand">
            <Lock className="h-5 w-5" />
          </div>
          <h1 className="mt-4 text-lg font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-muted">
            {t("lockedTitle")} {t("lockedHint")}
          </p>
          <form onSubmit={submit} className="mt-5 grid gap-2.5">
            {/* A placeholder is not an accessible name: it disappears on focus
                and screen readers treat it inconsistently. The label is real
                markup, just visually hidden — the heading above already says
                what this field is. */}
            <label htmlFor="kit-password" className="sr-only">
              {t("lockedPassword")}
            </label>
            <Input
              id="kit-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={t("lockedPassword")}
              autoFocus
              required
            />
            <Button type="submit" disabled={busy} className="w-full">
              {busy ? t("checking") : t("lockedSubmit")}
            </Button>
          </form>
          {error && <p className="mt-3 text-sm text-danger">{error}</p>}
        </Card>
      </main>
    </div>
  );
}
