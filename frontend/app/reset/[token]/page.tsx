"use client";

import { use, useState } from "react";
import Link from "next/link";
import { Button, Card, Input, Label } from "@/app/_components/ui";
import { LocaleSwitch } from "@/app/_components/LocaleSwitch";
import { translator } from "@/app/_i18n";
import { appDict } from "@/app/_i18n/app";
import { useStoredLocale } from "@/app/_i18n/useLocale";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

/**
 * Choosing the new password.
 *
 * <p>The token is never validated before it is used. Checking it on load would
 * mean an endpoint that answers "this token is real", which is a free oracle
 * for anyone guessing -- and would burn a request on every crawler that follows
 * the link out of an inbox. It is spent once, on submit.
 */
export default function ResetPasswordPage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = use(params);
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  const [locale, setLocale] = useStoredLocale();
  const t = translator(appDict, locale);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/auth/password-reset/confirm`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, newPassword: password }),
      });
      if (res.ok) setDone(true);
      else if (res.status === 429) setError(t("resetThrottled"));
      else setError(t("resetInvalid"));
    } catch {
      setError(t("resetInvalid"));
    } finally {
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
          <h1 className="text-lg font-semibold tracking-tight">{t("resetTitle")}</h1>

          {done ? (
            <>
              <p className="mt-4 rounded-lg bg-brand-weak px-3 py-2.5 text-sm text-brand">
                {t("resetDone")}
              </p>
              <p className="mt-4 text-center text-sm">
                <Link href="/login" className="text-brand hover:underline">{t("navSignIn")}</Link>
              </p>
            </>
          ) : (
            <form onSubmit={submit} className="mt-5 grid gap-3">
              <div className="grid gap-1.5">
                <Label htmlFor="reset-password">{t("resetPassword")}</Label>
                <Input
                  id="reset-password"
                  type="password"
                  autoComplete="new-password"
                  minLength={8}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
              <Button type="submit" disabled={busy} className="w-full">
                {busy ? t("busy") : t("resetSubmit")}
              </Button>
              {error && <p className="text-sm text-danger">{error}</p>}
            </form>
          )}
        </Card>
      </div>
    </main>
  );
}
