"use client";

import Link from "next/link";
import { WifiOff } from "lucide-react";
import { Card } from "@/app/_components/ui";
import { translator } from "@/app/_i18n";
import { appDict } from "@/app/_i18n/app";
import { useStoredLocale } from "@/app/_i18n/useLocale";

/**
 * Shown when a signed-in app route is opened without a connection.
 *
 * <p>There is nothing useful to offer here beyond saying so plainly: every
 * dashboard view reads live data from the backend, so a cached shell would
 * only be an empty frame pretending to work.
 *
 * <p>The language comes from the stored preference rather than the account —
 * by definition there is no connection to ask the server with.
 */
export default function OfflinePage() {
  const [locale] = useStoredLocale();
  const t = translator(appDict, locale);

  return (
    <main className="grid min-h-screen place-items-center px-6 py-12">
      <Card className="w-full max-w-sm p-6 text-center">
        <span className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-page text-muted">
          <WifiOff className="h-6 w-6" />
        </span>
        <h1 className="mt-4 text-lg font-semibold tracking-tight">{t("offlineTitle")}</h1>
        <p className="mt-2 text-sm leading-relaxed text-muted">{t("offlineBody")}</p>
        <Link
          href="/dashboard"
          className="mt-5 inline-flex h-10 items-center justify-center rounded-xl bg-brand-strong px-4 text-sm font-medium text-white transition-opacity hover:opacity-90"
        >
          {t("offlineRetry")}
        </Link>
      </Card>
    </main>
  );
}
