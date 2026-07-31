"use client";

import { useState } from "react";
import Link from "next/link";
import { Button, Card, Input, Label } from "@/app/_components/ui";
import { LocaleSwitch } from "@/app/_components/LocaleSwitch";
import { translator } from "@/app/_i18n";
import { appDict } from "@/app/_i18n/app";
import { useStoredLocale } from "@/app/_i18n/useLocale";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [locale, setLocale] = useStoredLocale();
  const t = translator(appDict, locale);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, displayName }),
      });
      if (res.status === 409) {
        setError(t("registerEmailTaken"));
        return;
      }
      if (res.status === 400) {
        setError(t("registerInvalid"));
        return;
      }
      if (res.status === 429) {
        setError(t("loginThrottled"));
        return;
      }
      if (!res.ok) {
        setError(t("registerFailed"));
        return;
      }
      const data = await res.json();
      localStorage.setItem("token", data.token);
      window.location.href = "/dashboard";
    } catch {
      setError(t("loginUnreachable"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="grid min-h-screen place-items-center px-6 py-12">
      <div className="w-full max-w-sm">
        <Link href="/" className="mb-6 flex items-center justify-center gap-2">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-strong text-sm font-bold text-white">
            LM
          </span>
          <span className="font-semibold tracking-tight">LocalMediaKit</span>
        </Link>

        <div className="mb-4 flex justify-center">
          <LocaleSwitch locale={locale} onChange={setLocale} label={t("langLabel")} />
        </div>

        <Card className="p-6">
          <h1 className="text-xl font-semibold tracking-tight">{t("registerTitle")}</h1>
          <p className="mt-1 text-sm text-muted">{t("registerSubtitle")}</p>

          <form onSubmit={handleSubmit} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="name">{t("registerName")}</Label>
              <Input id="name" placeholder="" value={displayName}
                onChange={(e) => setDisplayName(e.target.value)} required />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="email">{t("loginEmail")}</Label>
              <Input id="email" type="email" placeholder="siz@ornek.com" value={email}
                onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="password">{t("loginPassword")}</Label>
              <Input id="password" type="password" placeholder={t("registerPasswordHint")} value={password}
                onChange={(e) => setPassword(e.target.value)} required />
            </div>
            <Button type="submit" disabled={busy} className="w-full">
              {busy ? t("busy") : t("registerSubmit")}
            </Button>
          </form>

          {error && <p className="mt-4 text-sm text-danger">{error}</p>}
        </Card>

        <p className="mt-4 text-center text-sm text-muted">
          {t("registerHaveAccount")}{" "}
          <Link href="/login" className="font-medium text-brand hover:underline">
            {t("registerSignIn")}
          </Link>
        </p>
      </div>
    </main>
  );
}
