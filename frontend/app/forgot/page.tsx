"use client";

import { useState } from "react";
import Link from "next/link";
import { Button, Card, Input, Label } from "@/app/_components/ui";
import { LocaleSwitch } from "@/app/_components/LocaleSwitch";
import { translator } from "@/app/_i18n";
import { appDict } from "@/app/_i18n/app";
import { useStoredLocale } from "@/app/_i18n/useLocale";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

/**
 * Asking for a reset link.
 *
 * <p>The confirmation is the same whatever happened: registered, unknown,
 * throttled. That is not vagueness for its own sake -- the backend answers the
 * same way for the same reason, and a page that said "no such account" would
 * hand back the membership check the API refuses to give.
 */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [locale, setLocale] = useStoredLocale();
  const t = translator(appDict, locale);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await fetch(`${BACKEND}/api/auth/password-reset/request`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
    } catch {
      // Even a network failure shows the same confirmation. Distinguishing it
      // would leak nothing useful and would invite retrying until it "worked".
    } finally {
      setSent(true);
      setBusy(false);
    }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-page px-5 text-fg">
      <div className="w-full max-w-sm">
        <div className="mb-3 flex justify-end">
          <LocaleSwitch locale={locale} onChange={setLocale} label={t("langLabel")} />
        </div>
        <Card className="p-7">
          <h1 className="text-lg font-semibold tracking-tight">{t("forgotTitle")}</h1>
          <p className="mt-1 text-sm text-muted">{t("forgotSubtitle")}</p>

          {sent ? (
            <p className="mt-5 rounded-lg bg-brand-weak px-3 py-2.5 text-sm text-brand">
              {t("forgotSent")}
            </p>
          ) : (
            <form onSubmit={submit} className="mt-5 grid gap-3">
              <div className="grid gap-1.5">
                <Label htmlFor="forgot-email">{t("loginEmail")}</Label>
                <Input
                  id="forgot-email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
              <Button type="submit" disabled={busy} className="w-full">
                {busy ? t("busy") : t("forgotSubmit")}
              </Button>
            </form>
          )}

          <p className="mt-4 text-center text-sm">
            <Link href="/login" className="text-brand hover:underline">
              {t("forgotBackToLogin")}
            </Link>
          </p>
        </Card>
      </div>
    </main>
  );
}
