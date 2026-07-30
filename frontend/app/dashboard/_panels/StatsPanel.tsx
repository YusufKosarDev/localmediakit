"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, RefreshCw, X } from "lucide-react";
import { Badge, Button, Input, Select } from "@/app/_components/ui";
import { BACKEND, authHeaders, del, errorMessage, get, nf, post, put } from "../_lib/api";
import { CATEGORIES, PLATFORMS, type DemoEntry, type Feedback, type Stat, type SyncStatus } from "../_lib/types";

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
  onStatsChanged,
}: {
  kitId: number;
  feedback: Feedback;
  /** Adding a measurement can complete an onboarding step. */
  onStatsChanged: () => Promise<void> | void;
}) {
  const [stats, setStats] = useState<Stat[]>([]);
  const [demoEntries, setDemoEntries] = useState<DemoEntry[]>([]);
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null);
  const [statForm, setStatForm] = useState({ ...emptyStatForm });
  const [channelInput, setChannelInput] = useState("");

  const load = useCallback(async () => {
    const [s, d, sy] = await Promise.all([
      get<Stat[]>(`/api/mediakits/${kitId}/stats`),
      get<DemoEntry[]>(`/api/mediakits/${kitId}/demographics`),
      get<SyncStatus>(`/api/mediakits/${kitId}/sources`),
    ]);
    if (s) setStats(s);
    if (d) setDemoEntries(d);
    if (sy) setSyncStatus(sy);
    setChannelInput("");
    setStatForm({ ...emptyStatForm });
  }, [kitId]);

  useEffect(() => {
    load();
  }, [load]);

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
      "Istatistik eklenemedi",
      201
    );
    if (result.ok) {
      await load();
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
      "Demografi kaydedilemedi"
    );
    if (result.ok) {
      if (result.data) setDemoEntries(result.data);
      feedback.notify("Demografi kaydedildi.");
    } else {
      feedback.fail(result.message);
    }
  }

  async function connectYouTube(e: React.FormEvent) {
    e.preventDefault();
    feedback.clear();
    const result = await put(`/api/mediakits/${kitId}/sources/YOUTUBE`, { externalId: channelInput }, "Baglanamadi");
    if (result.ok) {
      feedback.notify("Kanal baglandi; ilk olcum kaydedildi.");
      await load();
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
      feedback.notify(data.lastError ? `Senkron denendi: ${data.lastError}` : "Senkronlandi.");
      await load();
    } else if (res.status === 429) {
      feedback.fail("Cok kisa arayla senkron. Biraz bekleyin.");
    } else {
      feedback.fail(await errorMessage(res, "Senkronlanamadi"));
    }
  }

  async function disconnect(platform: string) {
    feedback.clear();
    const result = await del(`/api/mediakits/${kitId}/sources/${platform}`, "Kaldirilamadi");
    if (result.ok) await load();
    else feedback.fail(result.message);
  }

  const youtube = syncStatus?.sources.find((s) => s.platform === "YOUTUBE");

  return (
    <div className="grid gap-5">
      {syncStatus && syncStatus.availablePlatforms.includes("YOUTUBE") && (
        <div className="rounded-lg border border-line bg-surface p-3">
          <div className="mb-1 flex items-center gap-2 text-sm font-medium">
            <RefreshCw className="h-3.5 w-3.5 text-brand" /> Otomatik veri kaynagi — YouTube
          </div>
          {!youtube ? (
            <form onSubmit={connectYouTube} className="flex flex-wrap items-center gap-2">
              <Input
                required
                placeholder="@kanal-adi veya kanal ID"
                className="w-56"
                value={channelInput}
                onChange={(e) => setChannelInput(e.target.value)}
              />
              <Button type="submit" size="sm">Bagla</Button>
              <span className="text-xs text-faint">
                Baglaninca abone sayisi otomatik cekilir (gunluk otomatik).
              </span>
            </form>
          ) : (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-mono text-muted">{youtube.externalId}</span>
              {youtube.lastError ? (
                <Badge tone="danger">hata: {youtube.lastError}</Badge>
              ) : (
                youtube.lastSyncedAt && (
                  <span className="text-xs text-faint">
                    son senkron: {new Date(youtube.lastSyncedAt).toLocaleString("tr-TR")}
                  </span>
                )
              )}
              <Button size="sm" variant="secondary" onClick={() => syncNow("YOUTUBE")}>
                <RefreshCw className="h-3.5 w-3.5" /> Simdi senkronla
              </Button>
              <Button size="sm" variant="ghost" onClick={() => disconnect("YOUTUBE")}>
                Baglantiyi kes
              </Button>
            </div>
          )}
        </div>
      )}

      <div>
        <div className="mb-2 text-sm font-medium">Platform istatistikleri</div>
        {stats.length === 0 && <p className="text-sm text-muted">Henuz istatistik yok.</p>}
        <div className="grid gap-2">
          {stats.map((s) => (
            <div
              key={s.platform}
              className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-line bg-surface px-3 py-2 text-sm"
            >
              <span className="font-medium">{s.platform}</span>
              <span className="tabular-nums text-muted">{nf(s.followers)} takipci</span>
              {s.engagementRate != null && <span className="text-muted">%{s.engagementRate} etkilesim</span>}
              {s.followerGrowth30d != null && (
                <span className={s.followerGrowth30d >= 0 ? "text-success" : "text-danger"}>
                  {s.followerGrowth30d >= 0 ? "+" : ""}
                  {s.followerGrowth30d}% · 30g
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
          <Input required type="number" min={0} placeholder="takipci *" className="w-28"
            value={statForm.followers} onChange={(e) => setStatForm({ ...statForm, followers: e.target.value })} />
          <Input type="number" min={0} placeholder="ort. izlenme" className="w-28"
            value={statForm.avgViews} onChange={(e) => setStatForm({ ...statForm, avgViews: e.target.value })} />
          <Input type="number" min={0} placeholder="ort. begeni" className="w-28"
            value={statForm.avgLikes} onChange={(e) => setStatForm({ ...statForm, avgLikes: e.target.value })} />
          <Input type="number" min={0} placeholder="ort. yorum" className="w-28"
            value={statForm.avgComments} onChange={(e) => setStatForm({ ...statForm, avgComments: e.target.value })} />
          <Button type="submit" size="sm"><Plus className="h-3.5 w-3.5" /> Olcum ekle</Button>
        </form>
      </div>

      <div>
        <div className="mb-2 text-sm font-medium">Kitle (demografi)</div>
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
              <Input placeholder="etiket" className="w-32" value={d.label}
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
            <Plus className="h-3.5 w-3.5" /> Satir ekle
          </Button>
          <Button size="sm" onClick={saveDemographics}>Demografiyi kaydet</Button>
        </div>
      </div>
    </div>
  );
}
