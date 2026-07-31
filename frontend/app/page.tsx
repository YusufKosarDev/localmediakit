"use client";

import Link from "next/link";
import { ArrowRight, BarChart3, Users, Handshake, ShieldCheck } from "lucide-react";
import { Card } from "@/app/_components/ui";
import { LocaleSwitch } from "@/app/_components/LocaleSwitch";
import { translator } from "@/app/_i18n";
import { appDict } from "@/app/_i18n/app";
import { useStoredLocale } from "@/app/_i18n/useLocale";

/**
 * Marketing page.
 *
 * <p>A client component so the language toggle can work without the page
 * becoming dynamic. The static HTML — the version search engines index — is
 * rendered in the default language; a visitor who has switched before sees one
 * paint of Turkish before their choice is applied. Reading the preference any
 * earlier would need middleware or headers(), which would cost this page its
 * static generation.
 */
export default function Home() {
  const [locale, setLocale] = useStoredLocale();
  const t = translator(appDict, locale);

  const features = [
    { icon: BarChart3, title: t("featureStatsTitle"), body: t("featureStatsBody") },
    { icon: Users, title: t("featureAudienceTitle"), body: t("featureAudienceBody") },
    { icon: Handshake, title: t("featureCollabsTitle"), body: t("featureCollabsBody") },
  ];

  return (
    <main className="mx-auto flex min-h-screen max-w-5xl flex-col px-6">
      <header className="flex items-center justify-between py-6">
        <div className="flex items-center gap-2">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-strong text-sm font-bold text-white">
            LM
          </span>
          <span className="font-semibold tracking-tight">LocalMediaKit</span>
        </div>
        <nav className="flex items-center gap-1 text-sm">
          <LocaleSwitch locale={locale} onChange={setLocale} label={t("langLabel")} />
          <Link href="/login" className="rounded-lg px-3 py-2 text-muted transition-colors hover:text-fg">
            {t("navSignIn")}
          </Link>
          <Link
            href="/register"
            className="rounded-lg bg-fg px-3 py-2 font-medium text-page transition-opacity hover:opacity-90"
          >
            {t("navSignUp")}
          </Link>
        </nav>
      </header>

      <section className="flex flex-col items-center py-16 text-center sm:py-24">
        <span className="mb-5 inline-flex items-center gap-2 rounded-full border border-line bg-surface px-3 py-1 text-xs text-muted">
          <span className="h-1.5 w-1.5 rounded-full bg-success" />
          {t("heroBadge")}
        </span>
        <h1 className="max-w-2xl text-4xl font-semibold tracking-tight sm:text-6xl">
          {t("heroTitleBefore")}
          <span className="text-brand">{t("heroTitleAccent")}</span>
          {t("heroTitleAfter")}
        </h1>
        <p className="mt-5 max-w-xl text-lg text-muted">{t("heroBody")}</p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <Link
            href="/demo"
            className="inline-flex h-11 items-center gap-2 rounded-xl bg-brand-strong px-5 font-medium text-white shadow-sm transition-opacity hover:opacity-90"
          >
            {t("heroSeeDemo")} <ArrowRight className="h-4 w-4" />
          </Link>
          <Link
            href="/login"
            className="inline-flex h-11 items-center gap-2 rounded-xl border border-line bg-surface px-5 font-medium transition-colors hover:bg-page"
          >
            {t("heroBrowseDemo")}
          </Link>
        </div>
      </section>

      <section className="grid gap-4 pb-16 sm:grid-cols-3">
        {features.map((f) => (
          <Card key={f.title} className="p-6">
            <div className="mb-4 grid h-10 w-10 place-items-center rounded-xl bg-brand-weak text-brand">
              <f.icon className="h-5 w-5" />
            </div>
            <h3 className="font-semibold">{f.title}</h3>
            <p className="mt-1.5 text-sm leading-relaxed text-muted">{f.body}</p>
          </Card>
        ))}
      </section>

      <footer className="mt-auto flex flex-wrap items-center gap-2 border-t border-line py-6 text-xs text-faint">
        <ShieldCheck className="h-3.5 w-3.5" />
        {t("footer")}
      </footer>
    </main>
  );
}
