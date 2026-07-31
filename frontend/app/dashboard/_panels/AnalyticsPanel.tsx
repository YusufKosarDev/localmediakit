"use client";

import { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { Eye } from "lucide-react";
import { BACKEND, authHeaders, errorMessage } from "../_lib/api";
import { formatNumber, type Locale } from "@/app/_i18n";
import type { Analytics, Feedback, Translate } from "../_lib/types";

// recharts is heavy and only needed here — loaded on demand so it stays out of
// the initial dashboard bundle (and never reaches another page). Keeping these
// imports inside this panel is what confines recharts to this tab.
const chartFallback = (h: number) => {
  const ChartFallback = () => <div className="animate-pulse rounded-lg bg-page" style={{ height: h }} />;
  return ChartFallback;
};
const ViewsTrend = dynamic(() => import("../_AnalyticsCharts").then((m) => m.ViewsTrend), {
  ssr: false,
  loading: chartFallback(180),
});
const ReferrerBars = dynamic(() => import("../_AnalyticsCharts").then((m) => m.ReferrerBars), {
  ssr: false,
  loading: chartFallback(120),
});
const DeviceBars = dynamic(() => import("../_AnalyticsCharts").then((m) => m.DeviceBars), {
  ssr: false,
  loading: chartFallback(90),
});

/** Visitor counters and trend charts for one kit's published page. */
export function AnalyticsPanel({ kitId, feedback, t, locale }: { kitId: number; feedback: Feedback; t: Translate; locale: Locale }) {
  const [analytics, setAnalytics] = useState<Analytics | null>(null);

  const load = useCallback(async () => {
    feedback.clear();
    // Read directly: a failed analytics load has always surfaced a message,
    // unlike the other panels' silent loaders.
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/analytics`, { headers: authHeaders() });
    if (res.ok) setAnalytics(await res.json());
    else feedback.fail(await errorMessage(res, t("failedLoadAnalytics")));
    // feedback identity is stable per render of the shell; kitId is what
    // should re-trigger a load.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kitId]);

  useEffect(() => {
    load();
  }, [load]);

  if (!analytics) return null;

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap gap-3">
        <div className="rounded-xl border border-line bg-surface px-4 py-3">
          <div className="text-2xl font-semibold tabular-nums">{formatNumber(analytics.totalViews, locale)}</div>
          <div className="flex items-center gap-1 text-xs text-muted">
            <Eye className="h-3 w-3" /> {t("totalViews")}
          </div>
        </div>
        {analytics.uniqueVisitors != null && (
          <div className="rounded-xl border border-line bg-surface px-4 py-3">
            <div className="text-2xl font-semibold tabular-nums">{formatNumber(analytics.uniqueVisitors, locale)}</div>
            <div className="text-xs text-muted">{t("uniqueVisitors")}</div>
          </div>
        )}
      </div>
      <div className="grid gap-4">
        {analytics.viewsByDay && analytics.viewsByDay.length > 0 && (
          <div>
            <div className="mb-1 text-xs font-medium uppercase tracking-wider text-faint">{t("last30Days")}</div>
            <ViewsTrend data={analytics.viewsByDay} />
          </div>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          {analytics.referrers && analytics.referrers.length > 0 && (
            <div>
              <div className="mb-1 text-xs font-medium uppercase tracking-wider text-faint">{t("referrers")}</div>
              <ReferrerBars data={analytics.referrers} />
            </div>
          )}
          {analytics.devices && analytics.devices.length > 0 && (
            <div>
              <div className="mb-1 text-xs font-medium uppercase tracking-wider text-faint">{t("devices")}</div>
              <DeviceBars data={analytics.devices} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
