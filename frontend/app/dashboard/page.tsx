"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  Send, Trash2, Lock, Unlock, ExternalLink, Plus, ArrowUp, ArrowDown,
  RefreshCw, Crown, LogOut, Eye, Sparkles, Globe, X,
} from "lucide-react";
import dynamic from "next/dynamic";
import { Button, Card, Input, Select, Badge, Label } from "@/app/_components/ui";

// recharts is heavy and only needed on the Analitik tab — load it on demand so
// it stays out of the initial dashboard bundle (and never reaches other pages).
const chartFallback = (h: number) => () =>
  <div className="animate-pulse rounded-lg bg-page" style={{ height: h }} />;
const ViewsTrend = dynamic(() => import("./_AnalyticsCharts").then((m) => m.ViewsTrend), { ssr: false, loading: chartFallback(180) });
const ReferrerBars = dynamic(() => import("./_AnalyticsCharts").then((m) => m.ReferrerBars), { ssr: false, loading: chartFallback(120) });
const DeviceBars = dynamic(() => import("./_AnalyticsCharts").then((m) => m.DeviceBars), { ssr: false, loading: chartFallback(90) });

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

type Me = { id: number; email: string; displayName: string; plan: string };
type Kit = {
  id: number; slug: string; title: string; headline: string | null;
  avatarUrl: string | null; theme: string; status: string;
  publishedSlug: string | null; passwordProtected: boolean; contactEnabled: boolean;
};
type Version = { version: number; slug: string; publishedAt: string; active: boolean };
type Stat = {
  platform: string; followers: number; avgViews: number | null; avgLikes: number | null;
  avgComments: number | null; engagementRate: number | null; followerGrowth30d: number | null;
};
type DemoEntry = { category: string; label: string; percentage: number | string };
type Domain = {
  id: number; domain: string; status: string; attempts: number; lastCheckedAt: string | null;
  dnsRecordType: string; dnsRecordHost: string; dnsRecordValue: string;
};
type Analytics = {
  plan: string; totalViews: number; uniqueVisitors: number | null;
  viewsByDay: { date: string; views: number; uniqueVisitors: number }[] | null;
  referrers: { label: string; count: number }[] | null;
  devices: { label: string; count: number }[] | null;
};
type Collab = {
  id: number; brandName: string; campaign: string | null; period: string | null;
  resultNote: string | null; logoUrl: string | null; displayOrder: number;
};
type Billing = { plan: string; subscriptionStatus: string | null; currentPeriodEnd: string | null; stripeEnabled: boolean };
type RateItem = {
  id: number; serviceName: string; priceAmount: number | string;
  currency: string; note: string | null; displayOrder: number;
};
type Lead = { id: number; brandName: string; email: string; message: string; status: string; createdAt: string };
type SyncSource = { platform: string; externalId: string; lastSyncedAt: string | null; lastError: string | null };
type SyncStatus = { availablePlatforms: string[]; autoSync: boolean; sources: SyncSource[] };
type MetricChange = { metric: string; from: string | null; to: string | null };
type VersionDiff = {
  fromVersion: number; toVersion: number;
  fields: { field: string; from: string | null; to: string | null }[];
  platforms: { platform: string; kind: string; changes: MetricChange[] }[];
  collaborations: { added: string[]; removed: string[]; changed: MetricChange[] };
  rateCard: { added: string[]; removed: string[]; changed: MetricChange[] };
  demographics: { added: string[]; removed: string[]; changed: MetricChange[] };
};
type Tab = "edit" | "stats" | "collabs" | "leads" | "analytics" | "versions" | "domain";

const PLATFORMS = ["YOUTUBE", "INSTAGRAM", "TIKTOK"];
const CATEGORIES = ["AGE", "GENDER", "COUNTRY"];
const emptyStatForm = { platform: "YOUTUBE", followers: "", avgViews: "", avgLikes: "", avgComments: "" };
const TABS: { id: Tab; label: string }[] = [
  { id: "edit", label: "Duzenle" },
  { id: "stats", label: "Istatistik & Kitle" },
  { id: "collabs", label: "Isbirlikleri & Ucretler" },
  { id: "leads", label: "Gelen Kutusu" },
  { id: "analytics", label: "Analitik" },
  { id: "versions", label: "Versiyonlar" },
  { id: "domain", label: "Domain" },
];

function authHeaders(): HeadersInit {
  const token = typeof window !== "undefined" ? localStorage.getItem("token") : null;
  return { "Content-Type": "application/json", Authorization: `Bearer ${token ?? ""}` };
}
const nf = (n: number) => n.toLocaleString("tr-TR");

export default function DashboardPage() {
  const [me, setMe] = useState<Me | null>(null);
  const [kits, setKits] = useState<Kit[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [form, setForm] = useState({ title: "", headline: "", avatarUrl: "", theme: "light", slug: "" });
  const [active, setActive] = useState<{ kitId: number; tab: Tab } | null>(null);
  const [versions, setVersions] = useState<Version[]>([]);
  const [stats, setStats] = useState<Stat[]>([]);
  const [statForm, setStatForm] = useState({ ...emptyStatForm });
  const [demoEntries, setDemoEntries] = useState<DemoEntry[]>([]);
  const [collabs, setCollabs] = useState<Collab[]>([]);
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [domains, setDomains] = useState<Domain[]>([]);
  const [domainInput, setDomainInput] = useState("");
  const [billing, setBilling] = useState<Billing | null>(null);
  const [collabForm, setCollabForm] = useState({ brandName: "", campaign: "", period: "", resultNote: "" });
  const [rates, setRates] = useState<RateItem[]>([]);
  const [rateForm, setRateForm] = useState({ serviceName: "", priceAmount: "", currency: "TRY", note: "" });
  const [leads, setLeads] = useState<Lead[]>([]);
  const [diffSel, setDiffSel] = useState({ from: "", to: "" });
  const [diff, setDiff] = useState<VersionDiff | null>(null);
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null);
  const [channelInput, setChannelInput] = useState("");

  const loadKits = useCallback(async () => {
    const res = await fetch(`${BACKEND}/api/mediakits`, { headers: authHeaders() });
    if (res.ok) setKits(await res.json());
  }, []);

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (!token) {
      setError("Oturum yok.");
      return;
    }
    fetch(`${BACKEND}/api/me`, { headers: authHeaders() })
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((data: Me) => {
        setMe(data);
        return loadKits();
      })
      .catch(() => setError("Oturum gecersiz veya suresi dolmus."));
    fetch(`${BACKEND}/api/billing`, { headers: authHeaders() })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => data && setBilling(data))
      .catch(() => {});
    const upgrade = new URLSearchParams(window.location.search).get("upgrade");
    if (upgrade === "success") setNotice("Odeme alindi (test modu). Plan birkac saniye icinde PRO olur; yenileyin.");
    else if (upgrade === "cancelled") setNotice("Odeme iptal edildi; plan degismedi.");
  }, [loadKits]);

  async function startUpgrade() {
    setError("");
    if (billing?.stripeEnabled) {
      const res = await fetch(`${BACKEND}/api/billing/checkout`, { method: "POST", headers: authHeaders() });
      if (res.ok) {
        const data = await res.json();
        window.location.href = data.url;
      } else {
        const data = await res.json().catch(() => null);
        setError(data?.error ?? `Upgrade baslatilamadi (HTTP ${res.status})`);
      }
      return;
    }
    await demoPlanSwitch("demo-upgrade");
  }

  async function demoPlanSwitch(path: "demo-upgrade" | "demo-downgrade") {
    setError("");
    const res = await fetch(`${BACKEND}/api/billing/${path}`, { method: "POST", headers: authHeaders() });
    if (res.ok) {
      const data = await res.json();
      setBilling(data);
      setMe((prev) => (prev ? { ...prev, plan: data.plan } : prev));
      setNotice(data.plan === "PRO" ? "Plan PRO yapildi (demo modu — gercek odeme yok)." : "Plan FREE yapildi (demo modu).");
      await loadKits();
    } else {
      const data = await res.json().catch(() => null);
      setError(data?.error ?? `Plan degistirilemedi (HTTP ${res.status})`);
    }
  }

  async function createKit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits`, {
      method: "POST", headers: authHeaders(), body: JSON.stringify(form),
    });
    if (res.status === 201) {
      setForm({ title: "", headline: "", avatarUrl: "", theme: "light", slug: "" });
      await loadKits();
    } else {
      const data = await res.json().catch(() => null);
      setError(data?.error ?? `Olusturulamadi (HTTP ${res.status})`);
    }
  }

  async function saveKit(kit: Kit) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kit.id}`, {
      method: "PUT", headers: authHeaders(),
      body: JSON.stringify({ title: kit.title, headline: kit.headline, avatarUrl: kit.avatarUrl, theme: kit.theme, slug: kit.slug, contactEnabled: kit.contactEnabled }),
    });
    if (res.ok) { setNotice("Kaydedildi."); await loadKits(); }
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Kaydedilemedi (HTTP ${res.status})`); }
  }

  async function publishKit(id: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${id}/publish`, { method: "POST", headers: authHeaders() });
    if (res.ok) {
      setNotice("Yayinlandi.");
      await loadKits();
      if (active?.kitId === id && active.tab === "versions") await loadVersions(id);
    } else { const d = await res.json().catch(() => null); setError(d?.error ?? `Yayinlanamadi (HTTP ${res.status})`); }
  }

  async function loadVersions(id: number) {
    const res = await fetch(`${BACKEND}/api/mediakits/${id}/versions`, { headers: authHeaders() });
    if (res.ok) setVersions(await res.json());
    setDiff(null);
    setDiffSel({ from: "", to: "" });
  }
  async function loadDiff(kitId: number) {
    setError(""); setDiff(null);
    const res = await fetch(
      `${BACKEND}/api/mediakits/${kitId}/versions/diff?from=${diffSel.from}&to=${diffSel.to}`,
      { headers: authHeaders() });
    if (res.ok) setDiff(await res.json());
    else if (res.status === 403) setError("Bu versiyon FREE gorunurluk penceresinin disinda (PRO ile tum gecmis).");
    else setError(`Karsilastirilamadi (HTTP ${res.status})`);
  }
  async function activateVersion(kitId: number, version: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/versions/${version}/activate`, { method: "POST", headers: authHeaders() });
    if (res.ok) { await loadKits(); await loadVersions(kitId); }
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Versiyona donulemedi (HTTP ${res.status})`); }
  }

  async function loadDomains(kitId: number) {
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/domains`, { headers: authHeaders() });
    if (res.ok) { setDomains(await res.json()); setDomainInput(""); }
  }
  async function addDomain(kitId: number, e: React.FormEvent) {
    e.preventDefault(); setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/domains`, {
      method: "POST", headers: authHeaders(), body: JSON.stringify({ domain: domainInput }),
    });
    if (res.status === 201) { setDomainInput(""); await loadDomains(kitId); }
    else if (res.status === 403) setError("Custom domain PRO ozelligidir.");
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Domain eklenemedi (HTTP ${res.status})`); }
  }
  async function checkDomain(kitId: number, domainId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/domains/${domainId}/check`, { method: "POST", headers: authHeaders() });
    if (res.ok) await loadDomains(kitId); else setError(`Kontrol edilemedi (HTTP ${res.status})`);
  }
  async function deleteDomain(kitId: number, domainId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/domains/${domainId}`, { method: "DELETE", headers: authHeaders() });
    if (res.status === 204) await loadDomains(kitId); else setError(`Silinemedi (HTTP ${res.status})`);
  }

  async function setKitPassword(kitId: number) {
    setError("");
    const pw = window.prompt("Bu kit icin sifre belirleyin (en az 4 karakter):");
    if (pw == null) return;
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/password`, {
      method: "PUT", headers: authHeaders(), body: JSON.stringify({ password: pw }),
    });
    if (res.status === 204) { await loadKits(); setNotice("Sifre kaydedildi. Public sayfaya yansimasi icin Yayinla."); }
    else if (res.status === 403) setError("Sifre korumasi PRO ozelligidir.");
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Sifre belirlenemedi (HTTP ${res.status})`); }
  }
  async function removeKitPassword(kitId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/password`, { method: "DELETE", headers: authHeaders() });
    if (res.status === 204) { await loadKits(); setNotice("Sifre kaldirildi. Public sayfaya yansimasi icin Yayinla."); }
    else setError(`Sifre kaldirilamadi (HTTP ${res.status})`);
  }

  async function loadStatsPanel(id: number) {
    const [sRes, dRes, cRes, rRes, syRes] = await Promise.all([
      fetch(`${BACKEND}/api/mediakits/${id}/stats`, { headers: authHeaders() }),
      fetch(`${BACKEND}/api/mediakits/${id}/demographics`, { headers: authHeaders() }),
      fetch(`${BACKEND}/api/mediakits/${id}/collaborations`, { headers: authHeaders() }),
      fetch(`${BACKEND}/api/mediakits/${id}/ratecard`, { headers: authHeaders() }),
      fetch(`${BACKEND}/api/mediakits/${id}/sources`, { headers: authHeaders() }),
    ]);
    if (sRes.ok) setStats(await sRes.json());
    if (dRes.ok) setDemoEntries(await dRes.json());
    if (cRes.ok) setCollabs(await cRes.json());
    if (rRes.ok) setRates(await rRes.json());
    if (syRes.ok) setSyncStatus(await syRes.json());
    setChannelInput("");
    setStatForm({ ...emptyStatForm });
    setCollabForm({ brandName: "", campaign: "", period: "", resultNote: "" });
    setRateForm({ serviceName: "", priceAmount: "", currency: "TRY", note: "" });
  }
  async function loadAnalytics(kitId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/analytics`, { headers: authHeaders() });
    if (res.ok) setAnalytics(await res.json()); else setError(`Analitik yuklenemedi (HTTP ${res.status})`);
  }

  async function addStat(kitId: number, e: React.FormEvent) {
    e.preventDefault(); setError("");
    const num = (v: string) => (v === "" ? null : Number(v));
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/stats`, {
      method: "POST", headers: authHeaders(),
      body: JSON.stringify({ platform: statForm.platform, followers: num(statForm.followers), avgViews: num(statForm.avgViews), avgLikes: num(statForm.avgLikes), avgComments: num(statForm.avgComments) }),
    });
    if (res.status === 201) await loadStatsPanel(kitId);
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Istatistik eklenemedi (HTTP ${res.status})`); }
  }
  async function saveDemographics(kitId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/demographics`, {
      method: "PUT", headers: authHeaders(),
      body: JSON.stringify({ entries: demoEntries.map((d) => ({ ...d, percentage: Number(d.percentage) })) }),
    });
    if (res.ok) { setDemoEntries(await res.json()); setNotice("Demografi kaydedildi."); }
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Demografi kaydedilemedi (HTTP ${res.status})`); }
  }
  async function addCollab(kitId: number, e: React.FormEvent) {
    e.preventDefault(); setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/collaborations`, {
      method: "POST", headers: authHeaders(), body: JSON.stringify({ ...collabForm, displayOrder: collabs.length }),
    });
    if (res.status === 201) await loadStatsPanel(kitId);
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Isbirligi eklenemedi (HTTP ${res.status})`); }
  }
  async function saveCollab(kitId: number, col: Collab) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/collaborations/${col.id}`, {
      method: "PUT", headers: authHeaders(),
      body: JSON.stringify({ brandName: col.brandName, campaign: col.campaign, period: col.period, resultNote: col.resultNote, logoUrl: col.logoUrl, displayOrder: col.displayOrder }),
    });
    if (!res.ok) { const d = await res.json().catch(() => null); setError(d?.error ?? `Kaydedilemedi (HTTP ${res.status})`); }
    return res.ok;
  }
  async function moveCollab(kitId: number, index: number, dir: -1 | 1) {
    const other = index + dir;
    if (other < 0 || other >= collabs.length) return;
    const a = { ...collabs[index], displayOrder: other };
    const b = { ...collabs[other], displayOrder: index };
    if ((await saveCollab(kitId, a)) && (await saveCollab(kitId, b))) await loadStatsPanel(kitId);
  }
  async function deleteCollab(kitId: number, collabId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/collaborations/${collabId}`, { method: "DELETE", headers: authHeaders() });
    if (res.status === 204) await loadStatsPanel(kitId); else setError(`Silinemedi (HTTP ${res.status})`);
  }

  async function connectYouTube(kitId: number, e: React.FormEvent) {
    e.preventDefault(); setError(""); setNotice("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/sources/YOUTUBE`, {
      method: "PUT", headers: authHeaders(), body: JSON.stringify({ externalId: channelInput }),
    });
    if (res.ok) { setNotice("Kanal baglandi; ilk olcum kaydedildi."); await loadStatsPanel(kitId); }
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Baglanamadi (HTTP ${res.status})`); }
  }
  async function syncSourceNow(kitId: number, platform: string) {
    setError(""); setNotice("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/sources/${platform}/sync`, {
      method: "POST", headers: authHeaders(),
    });
    if (res.ok) {
      const data: SyncSource = await res.json();
      setNotice(data.lastError ? `Senkron denendi: ${data.lastError}` : "Senkronlandi.");
      await loadStatsPanel(kitId);
    } else if (res.status === 429) setError("Cok kisa arayla senkron. Biraz bekleyin.");
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Senkronlanamadi (HTTP ${res.status})`); }
  }
  async function disconnectSource(kitId: number, platform: string) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/sources/${platform}`, {
      method: "DELETE", headers: authHeaders(),
    });
    if (res.status === 204) await loadStatsPanel(kitId); else setError(`Kaldirilamadi (HTTP ${res.status})`);
  }

  async function addRate(kitId: number, e: React.FormEvent) {
    e.preventDefault(); setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/ratecard`, {
      method: "POST", headers: authHeaders(),
      body: JSON.stringify({ serviceName: rateForm.serviceName, priceAmount: Number(rateForm.priceAmount), currency: rateForm.currency, note: rateForm.note || null, displayOrder: rates.length }),
    });
    if (res.status === 201) await loadStatsPanel(kitId);
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Ucret eklenemedi (HTTP ${res.status})`); }
  }
  async function saveRate(kitId: number, item: RateItem) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/ratecard/${item.id}`, {
      method: "PUT", headers: authHeaders(),
      body: JSON.stringify({ serviceName: item.serviceName, priceAmount: Number(item.priceAmount), currency: item.currency, note: item.note || null, displayOrder: item.displayOrder }),
    });
    if (res.ok) setNotice("Kaydedildi.");
    else { const d = await res.json().catch(() => null); setError(d?.error ?? `Kaydedilemedi (HTTP ${res.status})`); }
  }
  async function deleteRate(kitId: number, itemId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/ratecard/${itemId}`, { method: "DELETE", headers: authHeaders() });
    if (res.status === 204) await loadStatsPanel(kitId); else setError(`Silinemedi (HTTP ${res.status})`);
  }

  async function loadLeads(kitId: number) {
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/leads`, { headers: authHeaders() });
    if (res.ok) setLeads(await res.json());
  }
  async function setLeadStatus(kitId: number, leadId: number, status: string) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/leads/${leadId}/status`, {
      method: "PUT", headers: authHeaders(), body: JSON.stringify({ status }),
    });
    if (res.ok) await loadLeads(kitId); else setError(`Guncellenemedi (HTTP ${res.status})`);
  }
  async function deleteLead(kitId: number, leadId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/leads/${leadId}`, { method: "DELETE", headers: authHeaders() });
    if (res.status === 204) await loadLeads(kitId); else setError(`Silinemedi (HTTP ${res.status})`);
  }

  async function openPreview(kitId: number) {
    setError("");
    // Mint a short-lived signed link, then open it; the URL is built from our
    // own origin so the backend needs no frontend-host config.
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/preview-link`, { method: "POST", headers: authHeaders() });
    if (res.ok) {
      const data = await res.json();
      window.open(`/preview/${data.token}`, "_blank", "noopener");
    } else {
      const d = await res.json().catch(() => null);
      setError(d?.error ?? `Onizleme olusturulamadi (HTTP ${res.status})`);
    }
  }

  async function deleteKit(id: number) {
    setError("");
    if (!window.confirm("Bu kiti silmek istediginize emin misiniz?")) return;
    const res = await fetch(`${BACKEND}/api/mediakits/${id}`, { method: "DELETE", headers: authHeaders() });
    if (res.status === 204) { if (active?.kitId === id) setActive(null); await loadKits(); }
    else setError(`Silinemedi (HTTP ${res.status})`);
  }

  function updateKitField(id: number, field: keyof Kit, value: string) {
    setKits((prev) => prev.map((k) => (k.id === id ? { ...k, [field]: value } : k)));
  }
  function logout() { localStorage.removeItem("token"); window.location.href = "/login"; }

  async function openTab(kit: Kit, tab: Tab) {
    setNotice(""); setError("");
    if (active?.kitId === kit.id && active.tab === tab) { setActive(null); return; }
    setActive({ kitId: kit.id, tab });
    if (tab === "stats" || tab === "collabs") await loadStatsPanel(kit.id);
    else if (tab === "leads") await loadLeads(kit.id);
    else if (tab === "analytics") await loadAnalytics(kit.id);
    else if (tab === "versions") await loadVersions(kit.id);
    else if (tab === "domain") await loadDomains(kit.id);
  }

  if (!me) {
    return (
      <main className="grid min-h-screen place-items-center px-6 text-center">
        <div>
          <p className="text-muted">{error || "Yukleniyor..."}</p>
          <Link href="/login" className="mt-3 inline-block font-medium text-brand hover:underline">Giris yap</Link>
        </div>
      </main>
    );
  }
  const isPro = me.plan === "PRO";

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-line bg-surface/80 backdrop-blur">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-5 py-3">
          <Link href="/" className="flex items-center gap-2">
            <span className="grid h-7 w-7 place-items-center rounded-lg bg-brand-strong text-xs font-bold text-white">LM</span>
            <span className="font-semibold tracking-tight">LocalMediaKit</span>
          </Link>
          <div className="flex items-center gap-3">
            <Badge tone={isPro ? "brand" : "neutral"}>
              {isPro && <Crown className="h-3 w-3" />} {me.plan}
            </Badge>
            <span className="hidden text-sm text-muted sm:inline">{me.displayName}</span>
            <Button variant="ghost" size="sm" onClick={logout}><LogOut className="h-4 w-4" /> Cikis</Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-5 py-8">
        {/* Plan card */}
        <Card className="mb-6 p-5">
          {isPro ? (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="flex items-center gap-2 font-medium"><Crown className="h-4 w-4 text-brand" /> PRO plan</div>
                <p className="mt-0.5 text-sm text-muted">
                  Sinirsiz kit · detayli analitik · sifre korumasi · rozet yok
                  {billing?.subscriptionStatus && <> · durum: {billing.subscriptionStatus}</>}
                  {billing?.currentPeriodEnd && <> · donem sonu: {new Date(billing.currentPeriodEnd).toLocaleDateString("tr-TR")}</>}
                </p>
              </div>
              {!billing?.stripeEnabled && (
                <Button variant="secondary" size="sm" onClick={() => demoPlanSwitch("demo-downgrade")}>FREE&apos;ye don (demo)</Button>
              )}
            </div>
          ) : (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="font-medium">FREE plan</div>
                <p className="mt-0.5 text-sm text-muted">1 kit · toplam sayac · sayfada rozet. PRO ile hepsi acilir.</p>
              </div>
              <Button onClick={startUpgrade}>
                <Sparkles className="h-4 w-4" />
                {billing?.stripeEnabled ? "PRO'ya gec — $7/ay (test)" : "PRO'ya gec (demo)"}
              </Button>
            </div>
          )}
          {notice && <p className="mt-3 rounded-lg bg-success/10 px-3 py-2 text-sm text-success">{notice}</p>}
          {error && <p className="mt-3 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">{error}</p>}
        </Card>

        {/* Create kit */}
        <Card className="mb-6 p-5">
          <h2 className="mb-3 font-semibold">Yeni medya kiti</h2>
          <form onSubmit={createKit} className="grid gap-3 sm:grid-cols-2">
            <Input placeholder="Baslik *" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
            <Input placeholder="Headline" value={form.headline} onChange={(e) => setForm({ ...form, headline: e.target.value })} />
            <Input placeholder="Avatar URL" value={form.avatarUrl} onChange={(e) => setForm({ ...form, avatarUrl: e.target.value })} />
            <Select value={form.theme} onChange={(e) => setForm({ ...form, theme: e.target.value })}>
              <option value="light">Acik tema</option>
              <option value="dark">Koyu tema</option>
            </Select>
            <Input placeholder="Slug (opsiyonel)" value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} />
            <Button type="submit" className="sm:col-span-2 sm:justify-self-start"><Plus className="h-4 w-4" /> Olustur</Button>
          </form>
        </Card>

        <h2 className="mb-3 px-1 text-sm font-medium text-muted">Kitlerim ({kits.length})</h2>
        <div className="grid gap-4">
          {kits.map((kit) => (
            <Card key={kit.id} className="overflow-hidden">
              {/* Header */}
              <div className="flex flex-wrap items-center gap-3 p-4">
                <div className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-weak font-semibold text-brand">
                  {(kit.title || "?").charAt(0).toUpperCase()}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium">{kit.title}</span>
                    <Badge tone={kit.status === "PUBLISHED" ? "success" : "neutral"}>{kit.status}</Badge>
                    {kit.passwordProtected && <Badge tone="warning"><Lock className="h-3 w-3" /> sifreli</Badge>}
                  </div>
                  {kit.publishedSlug && (
                    <a href={`/${kit.publishedSlug}`} target="_blank" rel="noreferrer"
                      className="mt-0.5 inline-flex items-center gap-1 text-xs text-muted hover:text-brand">
                      /{kit.publishedSlug} <ExternalLink className="h-3 w-3" />
                    </a>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <Button size="sm" variant="secondary" onClick={() => openPreview(kit.id)}><Eye className="h-3.5 w-3.5" /> Onizle</Button>
                  <Button size="sm" onClick={() => publishKit(kit.id)}><Send className="h-3.5 w-3.5" /> Yayinla</Button>
                  <Button size="sm" variant="danger" onClick={() => deleteKit(kit.id)}><Trash2 className="h-3.5 w-3.5" /></Button>
                </div>
              </div>

              {/* Tab strip */}
              <div className="flex gap-1 overflow-x-auto border-t border-line px-2 py-1.5">
                {TABS.map((t) => {
                  const on = active?.kitId === kit.id && active.tab === t.id;
                  return (
                    <button key={t.id} onClick={() => openTab(kit, t.id)}
                      className={`shrink-0 rounded-lg px-3 py-1.5 text-sm transition-colors ${
                        on ? "bg-brand-weak font-medium text-brand" : "text-muted hover:text-fg hover:bg-page"}`}>
                      {t.label}{t.id === "domain" ? " ·" : ""}
                    </button>
                  );
                })}
              </div>

              {/* Tab content */}
              {active?.kitId === kit.id && (
                <div className="border-t border-line bg-page/40 p-4">
                  {active.tab === "edit" && (
                    <div className="grid max-w-xl gap-3">
                      <div className="grid gap-1.5">
                        <Label>Baslik</Label>
                        <Input value={kit.title} onChange={(e) => updateKitField(kit.id, "title", e.target.value)} />
                      </div>
                      <div className="grid gap-1.5">
                        <Label>Headline</Label>
                        <Input value={kit.headline ?? ""} onChange={(e) => updateKitField(kit.id, "headline", e.target.value)} />
                      </div>
                      <div className="grid gap-1.5">
                        <Label>Avatar URL</Label>
                        <Input value={kit.avatarUrl ?? ""} onChange={(e) => updateKitField(kit.id, "avatarUrl", e.target.value)} />
                      </div>
                      <div className="grid grid-cols-2 gap-3">
                        <div className="grid gap-1.5">
                          <Label>Tema</Label>
                          <Select value={kit.theme} onChange={(e) => updateKitField(kit.id, "theme", e.target.value)}>
                            <option value="light">Acik</option>
                            <option value="dark">Koyu</option>
                          </Select>
                        </div>
                        <div className="grid gap-1.5">
                          <Label>Slug</Label>
                          <Input value={kit.slug} onChange={(e) => updateKitField(kit.id, "slug", e.target.value)} />
                        </div>
                      </div>
                      <label className="flex items-center gap-2 text-sm">
                        <input
                          type="checkbox"
                          checked={kit.contactEnabled}
                          onChange={(e) => setKits((prev) => prev.map((k) => k.id === kit.id ? { ...k, contactEnabled: e.target.checked } : k))}
                          className="h-4 w-4 accent-[--brand-strong]"
                        />
                        Iletisim formu (marka teklifleri)
                        <span className="text-xs text-faint">— kapatmak alimi hemen durdurur; formun sayfadan kalkmasi icin Yayinla</span>
                      </label>
                      <div className="flex flex-wrap gap-2">
                        <Button onClick={() => saveKit(kit)}>Kaydet</Button>
                        {kit.passwordProtected ? (
                          <Button variant="secondary" onClick={() => removeKitPassword(kit.id)}><Unlock className="h-4 w-4" /> Sifreyi kaldir</Button>
                        ) : (
                          <Button variant="secondary" onClick={() => setKitPassword(kit.id)}>
                            <Lock className="h-4 w-4" /> Sifre koy{isPro ? "" : " (PRO)"}
                          </Button>
                        )}
                      </div>
                      <p className="text-xs text-faint">Not: degisiklikler public sayfaya ancak Yayinla ile yansir.</p>
                    </div>
                  )}

                  {active.tab === "stats" && (
                    <div className="grid gap-5">
                      {syncStatus && syncStatus.availablePlatforms.includes("YOUTUBE") && (
                        <div className="rounded-lg border border-line bg-surface p-3">
                          <div className="mb-1 flex items-center gap-2 text-sm font-medium">
                            <RefreshCw className="h-3.5 w-3.5 text-brand" /> Otomatik veri kaynagi — YouTube
                          </div>
                          {(() => {
                            const src = syncStatus.sources.find((s) => s.platform === "YOUTUBE");
                            if (!src) {
                              return (
                                <form onSubmit={(e) => connectYouTube(kit.id, e)} className="flex flex-wrap items-center gap-2">
                                  <Input required placeholder="@kanal-adi veya kanal ID" className="w-56"
                                    value={channelInput} onChange={(e) => setChannelInput(e.target.value)} />
                                  <Button type="submit" size="sm">Bagla</Button>
                                  <span className="text-xs text-faint">Baglaninca abone sayisi otomatik cekilir{syncStatus.autoSync ? " (gunluk otomatik)" : "; gunluk otomatik senkron PRO'da"}.</span>
                                </form>
                              );
                            }
                            return (
                              <div className="flex flex-wrap items-center gap-2 text-sm">
                                <span className="font-mono text-muted">{src.externalId}</span>
                                {src.lastError
                                  ? <Badge tone="danger">hata: {src.lastError}</Badge>
                                  : src.lastSyncedAt && <span className="text-xs text-faint">son senkron: {new Date(src.lastSyncedAt).toLocaleString("tr-TR")}</span>}
                                {!syncStatus.autoSync && <Badge tone="warning">otomatik senkron PRO</Badge>}
                                <Button size="sm" variant="secondary" onClick={() => syncSourceNow(kit.id, "YOUTUBE")}>
                                  <RefreshCw className="h-3.5 w-3.5" /> Simdi senkronla
                                </Button>
                                <Button size="sm" variant="ghost" onClick={() => disconnectSource(kit.id, "YOUTUBE")}>Baglantiyi kes</Button>
                              </div>
                            );
                          })()}
                        </div>
                      )}
                      <div>
                        <div className="mb-2 text-sm font-medium">Platform istatistikleri</div>
                        {stats.length === 0 && <p className="text-sm text-muted">Henuz istatistik yok.</p>}
                        <div className="grid gap-2">
                          {stats.map((s) => (
                            <div key={s.platform} className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-line bg-surface px-3 py-2 text-sm">
                              <span className="font-medium">{s.platform}</span>
                              <span className="tabular-nums text-muted">{nf(s.followers)} takipci</span>
                              {s.engagementRate != null && <span className="text-muted">%{s.engagementRate} etkilesim</span>}
                              {s.followerGrowth30d != null && (
                                <span className={s.followerGrowth30d >= 0 ? "text-success" : "text-danger"}>
                                  {s.followerGrowth30d >= 0 ? "+" : ""}{s.followerGrowth30d}% · 30g
                                </span>
                              )}
                            </div>
                          ))}
                        </div>
                        <form onSubmit={(e) => addStat(kit.id, e)} className="mt-3 flex flex-wrap items-center gap-2">
                          <Select value={statForm.platform} onChange={(e) => setStatForm({ ...statForm, platform: e.target.value })}>
                            {PLATFORMS.map((p) => <option key={p} value={p}>{p}</option>)}
                          </Select>
                          <Input required type="number" min={0} placeholder="takipci *" className="w-28" value={statForm.followers} onChange={(e) => setStatForm({ ...statForm, followers: e.target.value })} />
                          <Input type="number" min={0} placeholder="ort. izlenme" className="w-28" value={statForm.avgViews} onChange={(e) => setStatForm({ ...statForm, avgViews: e.target.value })} />
                          <Input type="number" min={0} placeholder="ort. begeni" className="w-28" value={statForm.avgLikes} onChange={(e) => setStatForm({ ...statForm, avgLikes: e.target.value })} />
                          <Input type="number" min={0} placeholder="ort. yorum" className="w-28" value={statForm.avgComments} onChange={(e) => setStatForm({ ...statForm, avgComments: e.target.value })} />
                          <Button type="submit" size="sm"><Plus className="h-3.5 w-3.5" /> Olcum ekle</Button>
                        </form>
                      </div>
                      <div>
                        <div className="mb-2 text-sm font-medium">Kitle (demografi)</div>
                        <div className="grid gap-2">
                          {demoEntries.map((d, i) => (
                            <div key={i} className="flex flex-wrap items-center gap-2">
                              <Select value={d.category} onChange={(e) => setDemoEntries(demoEntries.map((x, j) => j === i ? { ...x, category: e.target.value } : x))}>
                                {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                              </Select>
                              <Input placeholder="etiket" className="w-32" value={d.label} onChange={(e) => setDemoEntries(demoEntries.map((x, j) => j === i ? { ...x, label: e.target.value } : x))} />
                              <Input type="number" min={0} max={100} step="0.1" placeholder="%" className="w-20" value={d.percentage} onChange={(e) => setDemoEntries(demoEntries.map((x, j) => j === i ? { ...x, percentage: e.target.value } : x))} />
                              <Button size="sm" variant="ghost" onClick={() => setDemoEntries(demoEntries.filter((_, j) => j !== i))}><X className="h-4 w-4" /></Button>
                            </div>
                          ))}
                        </div>
                        <div className="mt-3 flex gap-2">
                          <Button size="sm" variant="secondary" onClick={() => setDemoEntries([...demoEntries, { category: "AGE", label: "", percentage: "" }])}><Plus className="h-3.5 w-3.5" /> Satir ekle</Button>
                          <Button size="sm" onClick={() => saveDemographics(kit.id)}>Demografiyi kaydet</Button>
                        </div>
                      </div>
                    </div>
                  )}

                  {active.tab === "collabs" && (
                    <div className="grid gap-3">
                      <div className="text-sm font-medium">Marka isbirlikleri</div>
                      {collabs.map((col, i) => (
                        <div key={col.id} className="flex flex-wrap items-center gap-2">
                          <Input placeholder="marka *" className="w-32" value={col.brandName} onChange={(e) => setCollabs(collabs.map((x, j) => j === i ? { ...x, brandName: e.target.value } : x))} />
                          <Input placeholder="kampanya" className="w-40" value={col.campaign ?? ""} onChange={(e) => setCollabs(collabs.map((x, j) => j === i ? { ...x, campaign: e.target.value } : x))} />
                          <Input placeholder="donem" className="w-24" value={col.period ?? ""} onChange={(e) => setCollabs(collabs.map((x, j) => j === i ? { ...x, period: e.target.value } : x))} />
                          <Input placeholder="sonuc" className="w-44" value={col.resultNote ?? ""} onChange={(e) => setCollabs(collabs.map((x, j) => j === i ? { ...x, resultNote: e.target.value } : x))} />
                          <Button size="sm" variant="secondary" onClick={() => saveCollab(kit.id, col)}>Kaydet</Button>
                          <Button size="sm" variant="ghost" onClick={() => moveCollab(kit.id, i, -1)} disabled={i === 0}><ArrowUp className="h-4 w-4" /></Button>
                          <Button size="sm" variant="ghost" onClick={() => moveCollab(kit.id, i, 1)} disabled={i === collabs.length - 1}><ArrowDown className="h-4 w-4" /></Button>
                          <Button size="sm" variant="ghost" onClick={() => deleteCollab(kit.id, col.id)}><Trash2 className="h-4 w-4" /></Button>
                        </div>
                      ))}
                      <form onSubmit={(e) => addCollab(kit.id, e)} className="flex flex-wrap items-center gap-2 border-t border-line pt-3">
                        <Input required placeholder="marka *" className="w-32" value={collabForm.brandName} onChange={(e) => setCollabForm({ ...collabForm, brandName: e.target.value })} />
                        <Input placeholder="kampanya" className="w-40" value={collabForm.campaign} onChange={(e) => setCollabForm({ ...collabForm, campaign: e.target.value })} />
                        <Input placeholder="donem" className="w-24" value={collabForm.period} onChange={(e) => setCollabForm({ ...collabForm, period: e.target.value })} />
                        <Input placeholder="sonuc" className="w-44" value={collabForm.resultNote} onChange={(e) => setCollabForm({ ...collabForm, resultNote: e.target.value })} />
                        <Button type="submit" size="sm"><Plus className="h-3.5 w-3.5" /> Ekle</Button>
                      </form>

                      <div className="mt-2 border-t border-line pt-4">
                        <div className="mb-2 text-sm font-medium">Calisma ucretleri (rate card)</div>
                        {rates.map((r, i) => (
                          <div key={r.id} className="mb-2 flex flex-wrap items-center gap-2">
                            <Input placeholder="hizmet *" className="w-44" value={r.serviceName} onChange={(e) => setRates(rates.map((x, j) => j === i ? { ...x, serviceName: e.target.value } : x))} />
                            <Input type="number" min={0} placeholder="fiyat *" className="w-28" value={r.priceAmount} onChange={(e) => setRates(rates.map((x, j) => j === i ? { ...x, priceAmount: e.target.value } : x))} />
                            <Select value={r.currency} onChange={(e) => setRates(rates.map((x, j) => j === i ? { ...x, currency: e.target.value } : x))}>
                              <option>TRY</option><option>USD</option><option>EUR</option>
                            </Select>
                            <Input placeholder="not" className="w-44" value={r.note ?? ""} onChange={(e) => setRates(rates.map((x, j) => j === i ? { ...x, note: e.target.value } : x))} />
                            <Button size="sm" variant="secondary" onClick={() => saveRate(kit.id, r)}>Kaydet</Button>
                            <Button size="sm" variant="ghost" onClick={() => deleteRate(kit.id, r.id)}><Trash2 className="h-4 w-4" /></Button>
                          </div>
                        ))}
                        <form onSubmit={(e) => addRate(kit.id, e)} className="flex flex-wrap items-center gap-2">
                          <Input required placeholder="hizmet *" className="w-44" value={rateForm.serviceName} onChange={(e) => setRateForm({ ...rateForm, serviceName: e.target.value })} />
                          <Input required type="number" min={0} placeholder="fiyat *" className="w-28" value={rateForm.priceAmount} onChange={(e) => setRateForm({ ...rateForm, priceAmount: e.target.value })} />
                          <Select value={rateForm.currency} onChange={(e) => setRateForm({ ...rateForm, currency: e.target.value })}>
                            <option>TRY</option><option>USD</option><option>EUR</option>
                          </Select>
                          <Input placeholder="not" className="w-44" value={rateForm.note} onChange={(e) => setRateForm({ ...rateForm, note: e.target.value })} />
                          <Button type="submit" size="sm"><Plus className="h-3.5 w-3.5" /> Ekle</Button>
                        </form>
                      </div>
                    </div>
                  )}

                  {active.tab === "leads" && (
                    <div className="grid gap-2.5">
                      {leads.length === 0 && (
                        <p className="text-sm text-muted">Henuz marka teklifi yok. Public sayfadaki iletisim formundan gelen teklifler burada listelenir.</p>
                      )}
                      {!isPro && leads.length >= 10 && (
                        <p className="rounded-lg bg-brand-weak px-3 py-2 text-sm text-brand">Son 10 teklif gosteriliyor — tum gecmis PRO planda.</p>
                      )}
                      {leads.map((l) => (
                        <div key={l.id} className={`rounded-lg border border-line bg-surface p-3 text-sm ${l.status === "ARCHIVED" ? "opacity-60" : ""}`}>
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-medium">{l.brandName}</span>
                            <a href={`mailto:${l.email}`} className="text-brand hover:underline">{l.email}</a>
                            {l.status === "NEW" && <Badge tone="brand">yeni</Badge>}
                            {l.status === "ARCHIVED" && <Badge tone="neutral">arsiv</Badge>}
                            <span className="ml-auto text-xs text-faint">{new Date(l.createdAt).toLocaleString("tr-TR")}</span>
                          </div>
                          <p className="mt-1.5 whitespace-pre-wrap text-muted">{l.message}</p>
                          <div className="mt-2 flex gap-2">
                            {l.status === "NEW" && (
                              <Button size="sm" variant="secondary" onClick={() => setLeadStatus(kit.id, l.id, "READ")}>Okundu</Button>
                            )}
                            {l.status !== "ARCHIVED" && (
                              <Button size="sm" variant="ghost" onClick={() => setLeadStatus(kit.id, l.id, "ARCHIVED")}>Arsivle</Button>
                            )}
                            <Button size="sm" variant="ghost" onClick={() => deleteLead(kit.id, l.id)}><Trash2 className="h-4 w-4" /></Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                  {active.tab === "analytics" && analytics && (
                    <div className="grid gap-4">
                      <div className="flex flex-wrap gap-3">
                        <div className="rounded-xl border border-line bg-surface px-4 py-3">
                          <div className="text-2xl font-semibold tabular-nums">{nf(analytics.totalViews)}</div>
                          <div className="flex items-center gap-1 text-xs text-muted"><Eye className="h-3 w-3" /> toplam goruntulenme</div>
                        </div>
                        {analytics.plan === "PRO" && analytics.uniqueVisitors != null && (
                          <div className="rounded-xl border border-line bg-surface px-4 py-3">
                            <div className="text-2xl font-semibold tabular-nums">{nf(analytics.uniqueVisitors)}</div>
                            <div className="text-xs text-muted">tekil ziyaretci</div>
                          </div>
                        )}
                      </div>
                      {analytics.plan !== "PRO" ? (
                        <p className="rounded-lg bg-brand-weak px-3 py-2 text-sm text-brand">
                          Gunluk grafik, referrer ve cihaz kirilimi PRO planda.
                        </p>
                      ) : (
                        <div className="grid gap-4">
                          {analytics.viewsByDay && analytics.viewsByDay.length > 0 && (
                            <div>
                              <div className="mb-1 text-xs font-medium uppercase tracking-wider text-faint">Son 30 gun</div>
                              <ViewsTrend data={analytics.viewsByDay} />
                            </div>
                          )}
                          <div className="grid gap-4 sm:grid-cols-2">
                            {analytics.referrers && analytics.referrers.length > 0 && (
                              <div>
                                <div className="mb-1 text-xs font-medium uppercase tracking-wider text-faint">Kaynaklar</div>
                                <ReferrerBars data={analytics.referrers} />
                              </div>
                            )}
                            {analytics.devices && analytics.devices.length > 0 && (
                              <div>
                                <div className="mb-1 text-xs font-medium uppercase tracking-wider text-faint">Cihazlar</div>
                                <DeviceBars data={analytics.devices} />
                              </div>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {active.tab === "versions" && (
                    <div className="grid gap-2">
                      {versions.length === 0 && <p className="text-sm text-muted">Henuz yayinlanmamis.</p>}
                      {versions.map((v) => (
                        <div key={v.version} className="flex flex-wrap items-center gap-3 rounded-lg border border-line bg-surface px-3 py-2 text-sm">
                          <span className="font-medium">v{v.version}</span>
                          <span className="text-muted">/{v.slug}</span>
                          <span className="text-xs text-faint">{new Date(v.publishedAt).toLocaleString("tr-TR")}</span>
                          {v.active ? <Badge tone="success">yayinda</Badge>
                            : <Button size="sm" variant="secondary" onClick={() => activateVersion(kit.id, v.version)}>Bu versiyona don</Button>}
                        </div>
                      ))}

                      {versions.length >= 2 && (
                        <div className="mt-2 rounded-lg border border-line bg-surface p-3">
                          <div className="flex flex-wrap items-center gap-2 text-sm">
                            <span className="font-medium">Karsilastir:</span>
                            <Select value={diffSel.from} onChange={(e) => setDiffSel({ ...diffSel, from: e.target.value })}>
                              <option value="">eski...</option>
                              {versions.map((v) => <option key={v.version} value={v.version}>v{v.version}</option>)}
                            </Select>
                            <span className="text-muted">→</span>
                            <Select value={diffSel.to} onChange={(e) => setDiffSel({ ...diffSel, to: e.target.value })}>
                              <option value="">yeni...</option>
                              {versions.map((v) => <option key={v.version} value={v.version}>v{v.version}</option>)}
                            </Select>
                            <Button size="sm" disabled={!diffSel.from || !diffSel.to || diffSel.from === diffSel.to}
                              onClick={() => loadDiff(kit.id)}>Goster</Button>
                          </div>

                          {diff && (
                            <div className="mt-3 grid gap-1.5 border-t border-line pt-3 text-sm">
                              {diff.fields.length === 0 && diff.platforms.length === 0
                                && diff.collaborations.added.length + diff.collaborations.removed.length === 0
                                && diff.rateCard.added.length + diff.rateCard.removed.length + diff.rateCard.changed.length === 0
                                && diff.demographics.added.length + diff.demographics.removed.length + diff.demographics.changed.length === 0 && (
                                <p className="text-muted">v{diff.fromVersion} ile v{diff.toVersion} arasinda icerik farki yok.</p>
                              )}
                              {diff.fields.map((f) => (
                                <div key={f.field}>
                                  <span className="text-faint">{f.field}:</span>{" "}
                                  <span className="text-danger line-through">{f.from ?? "—"}</span>{" "}
                                  <span className="text-success">{f.to ?? "—"}</span>
                                </div>
                              ))}
                              {diff.platforms.map((p) => (
                                <div key={p.platform}>
                                  <span className="font-medium">{p.platform}</span>{" "}
                                  {p.kind === "ADDED" && <Badge tone="success">eklendi</Badge>}
                                  {p.kind === "REMOVED" && <Badge tone="danger">cikti</Badge>}
                                  {p.changes.map((c) => (
                                    <span key={c.metric} className="ml-2 text-muted">
                                      {c.metric}: {c.from ?? "—"} → <span className="text-fg">{c.to ?? "—"}</span>
                                    </span>
                                  ))}
                                </div>
                              ))}
                              {([["Isbirlikleri", diff.collaborations], ["Ucretler", diff.rateCard], ["Demografi", diff.demographics]] as const)
                                .filter(([, d]) => d.added.length + d.removed.length + d.changed.length > 0)
                                .map(([label, d]) => (
                                  <div key={label}>
                                    <span className="font-medium">{label}:</span>{" "}
                                    {d.added.length > 0 && <span className="text-success">+{d.added.join(", ")}</span>}{" "}
                                    {d.removed.length > 0 && <span className="text-danger">−{d.removed.join(", ")}</span>}{" "}
                                    {d.changed.map((c) => (
                                      <span key={c.metric} className="ml-1 text-muted">
                                        {c.metric}: {c.from} → <span className="text-fg">{c.to}</span>
                                      </span>
                                    ))}
                                  </div>
                                ))}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )}

                  {active.tab === "domain" && (
                    <div className="grid gap-3">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium">Custom domain</span>
                        <Badge tone="warning">YAKINDA</Badge>
                      </div>
                      <p className="text-xs text-muted">
                        Kendi alan adinizi baglama ozelligi gelistirme asamasinda. DNS dogrulama altyapisi
                        (asenkron scheduled job) burada calisir; domain baglama henuz aktif degil.
                      </p>
                      <form onSubmit={(e) => addDomain(kit.id, e)} className="flex flex-wrap gap-2">
                        <Input placeholder="ornek: medyakit.alanadim.com" className="w-64" value={domainInput} onChange={(e) => setDomainInput(e.target.value)} required />
                        <Button type="submit" size="sm"><Globe className="h-3.5 w-3.5" /> Ekle</Button>
                      </form>
                      {domains.map((d) => (
                        <div key={d.id} className="rounded-lg border border-line bg-surface p-3 text-sm">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-medium">{d.domain}</span>
                            <Badge tone={d.status === "VERIFIED" ? "success" : d.status === "FAILED" ? "danger" : "warning"}>{d.status}</Badge>
                            {d.lastCheckedAt && <span className="text-xs text-faint">son kontrol: {new Date(d.lastCheckedAt).toLocaleString("tr-TR")} ({d.attempts})</span>}
                          </div>
                          {d.status !== "VERIFIED" && (
                            <div className="mt-2 rounded-lg bg-page p-2.5 text-xs text-muted">
                              DNS saglayicaniza su TXT kaydini ekleyin:
                              <div className="mt-1 break-all font-mono text-fg">{d.dnsRecordHost} = {d.dnsRecordValue}</div>
                            </div>
                          )}
                          <div className="mt-2 flex gap-2">
                            <Button size="sm" variant="secondary" onClick={() => checkDomain(kit.id, d.id)}><RefreshCw className="h-3.5 w-3.5" /> Simdi kontrol et</Button>
                            <Button size="sm" variant="ghost" onClick={() => deleteDomain(kit.id, d.id)}>Kaldir</Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </Card>
          ))}
        </div>
      </main>
    </div>
  );
}
