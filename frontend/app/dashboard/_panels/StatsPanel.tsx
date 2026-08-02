"use client";

import { useState } from "react";
import { Plus, RefreshCw, X } from "lucide-react";
import { Badge, Button, Input, Select } from "@/app/_components/ui";
import { BACKEND, authHeaders, del, errorMessage, get, post, put } from "../_lib/api";
import { useResource } from "../_lib/useResource";
import { formatDateTime, formatNumber, type Locale } from "@/app/_i18n";
import {
  CATEGORIES, PLATFORMS,
  type DemoEntry, type Feedback, type Stat, type SyncStatus, type Translate,
} from "../_lib/types";

/** Turkish writes the sign first (%6,47), English last (6.47%). */
function fmtPct(n: number, locale: Locale): string {
  const value = n.toLocaleString(locale === "tr" ? "tr-TR" : "en-US", { maximumFractionDigits: 2 });
  return locale === "tr" ? `%${value}` : `${value}%`;
}

const emptyStatForm = { platform: "YOUTUBE", followers: "", avgViews: "", avgLikes: "", avgComments: "" };

/**
 * Platform measurements, audience demographics and the automatic data source.
 *
 * <p>All three live on one screen ("Istatistik & Kitle"), so they live in one
 * file: splitting them would spread a single tab across three places without
 * making any of them easier to follow.
 */
export function StatsPanel({
  kitId,
  feedback,
  t,
  locale,
  onStatsChanged,
}: {
  kitId: number;
  feedback: Feedback;
  t: Translate;
  locale: Locale;
  /** Adding a measurement can complete an onboarding step. */
  onStatsChanged: () => Promise<void> | void;
}) {
  const [statForm, setStatForm] = useState({ ...emptyStatForm });
  const [channelInput, setChannelInput] = useState("");

  // Three reads that make up one screen, so one resource and one reload.
  const { data, reload, setData } = useResource(
    `stats-${kitId}`,
    async () => {
      const [s, d, sy] = await Promise.all([
        get<Stat[]>(`/api/mediakits/${kitId}/stats`),
        get<DemoEntry[]>(`/api/mediakits/${kitId}/demographics`),
        get<SyncStatus>(`/api/mediakits/${kitId}/sources`),
      ]);
      return { stats: s ?? [], demoEntries: d ?? [], syncStatus: sy };
    },
    { stats: [] as Stat[], demoEntries: [] as DemoEntry[], syncStatus: null as SyncStatus | null }
  );
  const { stats, demoEntries, syncStatus } = data;

  /**
   * The demographics table is edited row by row before it is saved, so it needs
   * a local setter. Named to match what it replaces, which keeps every call
   * site below exactly as it was -- this is a change of where the state lives,
   * not of what the panel does with it.
   */
  const setDemoEntries = (next: DemoEntry[]) => setData({ ...data, demoEntries: next });

  async function addStat(e: React.FormEvent) {
    e.preventDefault();
    feedback.clear();
    const num = (v: string) => (v === "" ? null : Number(v));
    const result = await post(
      `/api/mediakits/${kitId}/stats`,
      {
        platform: statForm.platform,
        followers: num(statForm.followers),
        avgViews: num(statForm.avgViews),
        avgLikes: num(statForm.avgLikes),
        avgComments: num(statForm.avgComments),
      },
      t("failedAddStat"),
      201
    );
    if (result.ok) {
      // Clearing the form belongs to submitting it, not to every reload: it
      // used to sit in the loader, so connecting a channel or removing a
      // measurement also emptied whatever was half-typed here.
      setStatForm({ ...emptyStatForm });
      await reload();
      await onStatsChanged();
    } else {
      feedback.fail(result.message);
    }
  }

  async function saveDemographics() {
    feedback.clear();
    const result = await put<DemoEntry[]>(
      `/api/mediakits/${kitId}/demographics`,
      { entries: demoEntries.map((d) => ({ ...d, percentage: Number(d.percentage) })) },
      t("failedSaveDemographics")
    );
    if (result.ok) {
      if (result.data) setDemoEntries(result.data);
      feedback.notify(t("demographicsSaved"));
    } else {
      feedback.fail(result.message);
    }
  }

  async function connectYouTube(e: React.FormEvent) {
    e.preventDefault();
    feedback.clear();
    const result = await put(`/api/mediakits/${kitId}/sources/YOUTUBE`, { externalId: channelInput }, t("failedConnect"));
    if (result.ok) {
      feedback.notify(t("syncConnected"));
      setChannelInput("");
      await reload();
    } else {
      feedback.fail(result.message);
    }
  }

  /**
   * Uses fetch directly rather than the shared helper: 429 needs its own
   * message ("you synced too recently"), which means reading the status before
   * the body is turned into a generic error.
   */
  async function syncNow(platform: string) {
    feedback.clear();
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/sources/${platform}/sync`, {
      method: "POST",
      headers: authHeaders(),
    });
    if (res.ok) {
      const data = await res.json();
      feedback.notify(data.lastError ? t("syncAttempted", { message: data.lastError }) : t("syncDone"));
      await reload();
    } else if (res.status === 429) {
      feedback.fail(t("syncTooSoon"));
    } else {
      feedback.fail(await errorMessage(res, t("failedSync")));
    }
  }

  async function disconnect(platform: string) {
    feedback.clear();
    const result = await del(`/api/mediakits/${kitId}/sources/${platform}`, t("failedDisconnect"));
    if (result.ok) await reload();
    else feedback.fail(result.message);
  }

  const youtube = syncStatus?.sources.find((s) => s.platform === "YOUTUBE");

  return (
    <div className="grid gap-5">
      {syncStatus && syncStatus.availablePlatforms.includes("YOUTUBE") && (
        <div className="rounded-lg border border-line bg-surface p-3">
          <div className="mb-1 flex items-center gap-2 text-sm font-medium">
            <RefreshCw className="h-3.5 w-3.5 text-brand" /> {t("syncSourceTitle")}
          </div>
          {!youtube ? (
            <form onSubmit={connectYouTube} className="flex flex-wrap items-center gap-2">
              <Input
                required
                placeholder={t("syncChannelPlaceholder")}
                className="w-56"
                value={channelInput}
                onChange={(e) => setChannelInput(e.target.value)}
              />
              <Button type="submit" size="sm">{t("syncConnect")}</Button>
              <span className="text-xs text-faint">
                {t("syncConnectHint")}
              </span>
            </form>
          ) : (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-mono text-muted">{youtube.externalId}</span>
              {youtube.lastError ? (
                <Badge tone="danger">{t("syncError", { message: youtube.lastError })}</Badge>
              ) : (
                youtube.lastSyncedAt && (
                  <span className="text-xs text-faint">
                    {t("syncLastSynced", { when: formatDateTime(youtube.lastSyncedAt, locale) })}
                  </span>
                )
              )}
              <Button size="sm" variant="secondary" onClick={() => syncNow("YOUTUBE")}>
                <RefreshCw className="h-3.5 w-3.5" /> {t("syncNow")}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => disconnect("YOUTUBE")}>
                {t("syncDisconnect")}
              </Button>
            </div>
          )}
        </div>
      )}

      <div>
        <div className="mb-2 text-sm font-medium">{t("platformStats")}</div>
        {stats.length === 0 && <p className="text-sm text-muted">{t("noStats")}</p>}
        <div className="grid gap-2">
          {stats.map((s) => (
            <div
              key={s.platform}
              className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-line bg-surface px-3 py-2 text-sm"
            >
              <span className="font-medium">{s.platform}</span>
              <span className="tabular-nums text-muted">{formatNumber(s.followers, locale)} {t("statFollowers")}</span>
              {s.engagementRate != null && <span className="text-muted">{fmtPct(s.engagementRate, locale)} {t("statEngagement")}</span>}
              {s.followerGrowth30d != null && (
                <span className={s.followerGrowth30d >= 0 ? "text-success" : "text-danger"}>
                  {s.followerGrowth30d >= 0 ? "+" : ""}
                  {formatNumber(s.followerGrowth30d, locale)}% · {t("statGrowth")}
                </span>
              )}
            </div>
          ))}
        </div>
        <form onSubmit={addStat} className="mt-3 flex flex-wrap items-center gap-2">
          <Select value={statForm.platform} onChange={(e) => setStatForm({ ...statForm, platform: e.target.value })}>
            {PLATFORMS.map((p) => (
              <option key={p} value={p}>{p}</option>
            ))}
          </Select>
          <Input required type="number" min={0} placeholder={t("fieldFollowers")} className="w-28"
            value={statForm.followers} onChange={(e) => setStatForm({ ...statForm, followers: e.target.value })} />
          <Input type="number" min={0} placeholder={t("fieldAvgViews")} className="w-28"
            value={statForm.avgViews} onChange={(e) => setStatForm({ ...statForm, avgViews: e.target.value })} />
          <Input type="number" min={0} placeholder={t("fieldAvgLikes")} className="w-28"
            value={statForm.avgLikes} onChange={(e) => setStatForm({ ...statForm, avgLikes: e.target.value })} />
          <Input type="number" min={0} placeholder={t("fieldAvgComments")} className="w-28"
            value={statForm.avgComments} onChange={(e) => setStatForm({ ...statForm, avgComments: e.target.value })} />
          <Button type="submit" size="sm"><Plus className="h-3.5 w-3.5" /> {t("addMeasurement")}</Button>
        </form>
      </div>

      <div>
        <div className="mb-2 text-sm font-medium">{t("demographics")}</div>
        <div className="grid gap-2">
          {demoEntries.map((d, i) => (
            <div key={i} className="flex flex-wrap items-center gap-2">
              <Select
                value={d.category}
                onChange={(e) => setDemoEntries(demoEntries.map((x, j) => (j === i ? { ...x, category: e.target.value } : x)))}
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </Select>
              <Input placeholder={t("fieldLabel")} className="w-32" value={d.label}
                onChange={(e) => setDemoEntries(demoEntries.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))} />
              <Input type="number" min={0} max={100} step="0.1" placeholder="%" className="w-20" value={d.percentage}
                onChange={(e) => setDemoEntries(demoEntries.map((x, j) => (j === i ? { ...x, percentage: e.target.value } : x)))} />
              <Button size="sm" variant="ghost" onClick={() => setDemoEntries(demoEntries.filter((_, j) => j !== i))}>
                <X className="h-4 w-4" />
              </Button>
            </div>
          ))}
        </div>
        <div className="mt-3 flex gap-2">
          <Button size="sm" variant="secondary"
            onClick={() => setDemoEntries([...demoEntries, { category: "AGE", label: "", percentage: "" }])}>
            <Plus className="h-3.5 w-3.5" /> {t("addRow")}
          </Button>
          <Button size="sm" onClick={saveDemographics}>{t("saveDemographics")}</Button>
        </div>
      </div>
    </div>
  );
}
