"use client";

import { useCallback, useEffect, useState } from "react";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

type Me = { id: number; email: string; displayName: string; plan: string };
type Kit = {
  id: number;
  slug: string;
  title: string;
  headline: string | null;
  avatarUrl: string | null;
  theme: string;
  status: string;
  publishedSlug: string | null;
};

type Version = {
  version: number;
  slug: string;
  publishedAt: string;
  active: boolean;
};

type Stat = {
  platform: string;
  followers: number;
  avgViews: number | null;
  avgLikes: number | null;
  avgComments: number | null;
  engagementRate: number | null;
  followerGrowth30d: number | null;
  recordedAt: string;
};

type DemoEntry = { category: string; label: string; percentage: number | string };

type Collab = {
  id: number;
  brandName: string;
  campaign: string | null;
  period: string | null;
  resultNote: string | null;
  logoUrl: string | null;
  displayOrder: number;
};

const PLATFORMS = ["YOUTUBE", "INSTAGRAM", "TIKTOK"];
const CATEGORIES = ["AGE", "GENDER", "COUNTRY"];
const emptyStatForm = { platform: "YOUTUBE", followers: "", avgViews: "", avgLikes: "", avgComments: "" };

function authHeaders(): HeadersInit {
  const token = typeof window !== "undefined" ? localStorage.getItem("token") : null;
  return { "Content-Type": "application/json", Authorization: `Bearer ${token ?? ""}` };
}

export default function DashboardPage() {
  const [me, setMe] = useState<Me | null>(null);
  const [kits, setKits] = useState<Kit[]>([]);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ title: "", headline: "", avatarUrl: "", theme: "light", slug: "" });
  const [versions, setVersions] = useState<Version[]>([]);
  const [versionsFor, setVersionsFor] = useState<number | null>(null);
  const [statsFor, setStatsFor] = useState<number | null>(null);
  const [stats, setStats] = useState<Stat[]>([]);
  const [statForm, setStatForm] = useState({ ...emptyStatForm });
  const [demoEntries, setDemoEntries] = useState<DemoEntry[]>([]);
  const [collabs, setCollabs] = useState<Collab[]>([]);
  const [collabForm, setCollabForm] = useState({ brandName: "", campaign: "", period: "", resultNote: "" });

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
  }, [loadKits]);

  async function createKit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(form),
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
      method: "PUT",
      headers: authHeaders(),
      body: JSON.stringify({
        title: kit.title,
        headline: kit.headline,
        avatarUrl: kit.avatarUrl,
        theme: kit.theme,
        slug: kit.slug,
      }),
    });
    if (res.ok) {
      await loadKits();
    } else {
      const data = await res.json().catch(() => null);
      setError(data?.error ?? `Kaydedilemedi (HTTP ${res.status})`);
    }
  }

  async function publishKit(id: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${id}/publish`, {
      method: "POST",
      headers: authHeaders(),
    });
    if (res.ok) {
      await loadKits();
      if (versionsFor === id) await loadVersions(id);
    } else {
      const data = await res.json().catch(() => null);
      setError(data?.error ?? `Yayinlanamadi (HTTP ${res.status})`);
    }
  }

  async function loadVersions(id: number) {
    const res = await fetch(`${BACKEND}/api/mediakits/${id}/versions`, { headers: authHeaders() });
    if (res.ok) {
      setVersions(await res.json());
      setVersionsFor(id);
    }
  }

  async function activateVersion(kitId: number, version: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/versions/${version}/activate`, {
      method: "POST",
      headers: authHeaders(),
    });
    if (res.ok) {
      await loadKits();
      await loadVersions(kitId);
    } else {
      const data = await res.json().catch(() => null);
      setError(data?.error ?? `Versiyona donulemedi (HTTP ${res.status})`);
    }
  }

  async function loadStatsPanel(id: number) {
    const [sRes, dRes, cRes] = await Promise.all([
      fetch(`${BACKEND}/api/mediakits/${id}/stats`, { headers: authHeaders() }),
      fetch(`${BACKEND}/api/mediakits/${id}/demographics`, { headers: authHeaders() }),
      fetch(`${BACKEND}/api/mediakits/${id}/collaborations`, { headers: authHeaders() }),
    ]);
    if (sRes.ok) setStats(await sRes.json());
    if (dRes.ok) setDemoEntries(await dRes.json());
    if (cRes.ok) setCollabs(await cRes.json());
    setStatForm({ ...emptyStatForm });
    setCollabForm({ brandName: "", campaign: "", period: "", resultNote: "" });
    setStatsFor(id);
  }

  async function addCollab(kitId: number, e: React.FormEvent) {
    e.preventDefault();
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/collaborations`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({ ...collabForm, displayOrder: collabs.length }),
    });
    if (res.status === 201) {
      await loadStatsPanel(kitId);
    } else {
      const data = await res.json().catch(() => null);
      setError(data?.error ?? `Isbirligi eklenemedi (HTTP ${res.status})`);
    }
  }

  async function saveCollab(kitId: number, col: Collab) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/collaborations/${col.id}`, {
      method: "PUT",
      headers: authHeaders(),
      body: JSON.stringify({
        brandName: col.brandName,
        campaign: col.campaign,
        period: col.period,
        resultNote: col.resultNote,
        logoUrl: col.logoUrl,
        displayOrder: col.displayOrder,
      }),
    });
    if (!res.ok) {
      const data = await res.json().catch(() => null);
      setError(data?.error ?? `Kaydedilemedi (HTTP ${res.status})`);
    }
    return res.ok;
  }

  async function moveCollab(kitId: number, index: number, dir: -1 | 1) {
    const other = index + dir;
    if (other < 0 || other >= collabs.length) return;
    const a = { ...collabs[index], displayOrder: other };
    const b = { ...collabs[other], displayOrder: index };
    if ((await saveCollab(kitId, a)) && (await saveCollab(kitId, b))) {
      await loadStatsPanel(kitId);
    }
  }

  async function deleteCollab(kitId: number, collabId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/collaborations/${collabId}`, {
      method: "DELETE",
      headers: authHeaders(),
    });
    if (res.status === 204) await loadStatsPanel(kitId);
    else setError(`Silinemedi (HTTP ${res.status})`);
  }

  async function addStat(kitId: number, e: React.FormEvent) {
    e.preventDefault();
    setError("");
    const num = (v: string) => (v === "" ? null : Number(v));
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/stats`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({
        platform: statForm.platform,
        followers: num(statForm.followers),
        avgViews: num(statForm.avgViews),
        avgLikes: num(statForm.avgLikes),
        avgComments: num(statForm.avgComments),
      }),
    });
    if (res.status === 201) {
      await loadStatsPanel(kitId);
    } else {
      const data = await res.json().catch(() => null);
      setError(data?.error ?? `Istatistik eklenemedi (HTTP ${res.status})`);
    }
  }

  async function saveDemographics(kitId: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${kitId}/demographics`, {
      method: "PUT",
      headers: authHeaders(),
      body: JSON.stringify({
        entries: demoEntries.map((d) => ({ ...d, percentage: Number(d.percentage) })),
      }),
    });
    if (res.ok) {
      setDemoEntries(await res.json());
    } else {
      const data = await res.json().catch(() => null);
      setError(data?.error ?? `Demografi kaydedilemedi (HTTP ${res.status})`);
    }
  }

  async function deleteKit(id: number) {
    setError("");
    const res = await fetch(`${BACKEND}/api/mediakits/${id}`, { method: "DELETE", headers: authHeaders() });
    if (res.status === 204) await loadKits();
    else setError(`Silinemedi (HTTP ${res.status})`);
  }

  function updateKitField(id: number, field: keyof Kit, value: string) {
    setKits((prev) => prev.map((k) => (k.id === id ? { ...k, [field]: value } : k)));
  }

  function logout() {
    localStorage.removeItem("token");
    window.location.href = "/login";
  }

  if (!me) {
    return (
      <main style={{ fontFamily: "system-ui, sans-serif", padding: 40 }}>
        <p style={{ color: "crimson" }}>{error || "Yukleniyor..."}</p>
        <p><a href="/login">Giris yap</a></p>
      </main>
    );
  }

  return (
    <main style={{ fontFamily: "system-ui, sans-serif", padding: 40, maxWidth: 720 }}>
      <div style={{ display: "flex", justifyContent: "space-between" }}>
        <div>Merhaba <strong>{me.displayName}</strong> ({me.plan}) — {me.email}</div>
        <button onClick={logout}>Cikis</button>
      </div>

      <h2>Yeni medya kiti</h2>
      <form onSubmit={createKit} style={{ display: "grid", gap: 6, maxWidth: 420 }}>
        <input placeholder="baslik *" value={form.title}
          onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        <input placeholder="headline" value={form.headline}
          onChange={(e) => setForm({ ...form, headline: e.target.value })} />
        <input placeholder="avatar url" value={form.avatarUrl}
          onChange={(e) => setForm({ ...form, avatarUrl: e.target.value })} />
        <input placeholder="tema (light/dark)" value={form.theme}
          onChange={(e) => setForm({ ...form, theme: e.target.value })} />
        <input placeholder="slug (opsiyonel)" value={form.slug}
          onChange={(e) => setForm({ ...form, slug: e.target.value })} />
        <button type="submit">Olustur</button>
      </form>
      {error && <p style={{ color: "crimson" }}>{error}</p>}

      <h2>Kitlerim ({kits.length})</h2>
      {kits.map((kit) => (
        <div key={kit.id} style={{ border: "1px solid #ccc", padding: 12, marginBottom: 10 }}>
          <div>
            <small>
              #{kit.id} — durum: <strong>{kit.status}</strong>
              {kit.publishedSlug && (
                <>
                  {" — canli: "}
                  <a href={`/${kit.publishedSlug}`} target="_blank" rel="noreferrer">
                    /{kit.publishedSlug}
                  </a>
                </>
              )}
            </small>
          </div>
          <div style={{ display: "grid", gap: 6, maxWidth: 420, marginTop: 6 }}>
            <input value={kit.title} onChange={(e) => updateKitField(kit.id, "title", e.target.value)} />
            <input placeholder="headline" value={kit.headline ?? ""}
              onChange={(e) => updateKitField(kit.id, "headline", e.target.value)} />
            <input placeholder="avatar url" value={kit.avatarUrl ?? ""}
              onChange={(e) => updateKitField(kit.id, "avatarUrl", e.target.value)} />
            <input value={kit.theme} onChange={(e) => updateKitField(kit.id, "theme", e.target.value)} />
            <input value={kit.slug} onChange={(e) => updateKitField(kit.id, "slug", e.target.value)} />
            <div style={{ display: "flex", gap: 8 }}>
              <button onClick={() => saveKit(kit)}>Kaydet</button>
              <button onClick={() => publishKit(kit.id)} style={{ fontWeight: 600 }}>
                Yayinla
              </button>
              <button onClick={() =>
                versionsFor === kit.id ? setVersionsFor(null) : loadVersions(kit.id)
              }>
                Versiyonlar
              </button>
              <button onClick={() =>
                statsFor === kit.id ? setStatsFor(null) : loadStatsPanel(kit.id)
              }>
                Istatistik & Kitle
              </button>
              <button onClick={() => deleteKit(kit.id)}>Sil</button>
            </div>
            {statsFor === kit.id && (
              <div style={{ marginTop: 8, borderTop: "1px dashed #ccc", paddingTop: 8 }}>
                <strong style={{ fontSize: 14 }}>Platform istatistikleri</strong>
                {stats.length === 0 && <div><small>Henuz istatistik yok.</small></div>}
                {stats.map((s) => (
                  <div key={s.platform}>
                    <small>
                      {s.platform}: {s.followers.toLocaleString("tr-TR")} takipci
                      {s.engagementRate != null && <> — etkilesim %{s.engagementRate}</>}
                      {s.followerGrowth30d != null && (
                        <> — 30 gun: {s.followerGrowth30d >= 0 ? "+" : ""}{s.followerGrowth30d}%</>
                      )}
                    </small>
                  </div>
                ))}
                <form onSubmit={(e) => addStat(kit.id, e)}
                  style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
                  <select value={statForm.platform}
                    onChange={(e) => setStatForm({ ...statForm, platform: e.target.value })}>
                    {PLATFORMS.map((p) => <option key={p} value={p}>{p}</option>)}
                  </select>
                  <input required type="number" min={0} placeholder="takipci *" style={{ width: 90 }}
                    value={statForm.followers}
                    onChange={(e) => setStatForm({ ...statForm, followers: e.target.value })} />
                  <input type="number" min={0} placeholder="ort. izlenme" style={{ width: 90 }}
                    value={statForm.avgViews}
                    onChange={(e) => setStatForm({ ...statForm, avgViews: e.target.value })} />
                  <input type="number" min={0} placeholder="ort. begeni" style={{ width: 90 }}
                    value={statForm.avgLikes}
                    onChange={(e) => setStatForm({ ...statForm, avgLikes: e.target.value })} />
                  <input type="number" min={0} placeholder="ort. yorum" style={{ width: 90 }}
                    value={statForm.avgComments}
                    onChange={(e) => setStatForm({ ...statForm, avgComments: e.target.value })} />
                  <button type="submit">Olcum ekle</button>
                </form>

                <strong style={{ fontSize: 14, display: "block", marginTop: 10 }}>Kitle (demografi)</strong>
                {demoEntries.map((d, i) => (
                  <div key={i} style={{ display: "flex", gap: 6, marginTop: 4 }}>
                    <select value={d.category}
                      onChange={(e) => setDemoEntries(demoEntries.map((x, j) =>
                        j === i ? { ...x, category: e.target.value } : x))}>
                      {CATEGORIES.map((cat) => <option key={cat} value={cat}>{cat}</option>)}
                    </select>
                    <input placeholder="etiket" value={d.label} style={{ width: 110 }}
                      onChange={(e) => setDemoEntries(demoEntries.map((x, j) =>
                        j === i ? { ...x, label: e.target.value } : x))} />
                    <input type="number" min={0} max={100} step="0.1" placeholder="%" style={{ width: 70 }}
                      value={d.percentage}
                      onChange={(e) => setDemoEntries(demoEntries.map((x, j) =>
                        j === i ? { ...x, percentage: e.target.value } : x))} />
                    <button onClick={() => setDemoEntries(demoEntries.filter((_, j) => j !== i))}>
                      Kaldir
                    </button>
                  </div>
                ))}
                <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
                  <button onClick={() =>
                    setDemoEntries([...demoEntries, { category: "AGE", label: "", percentage: "" }])
                  }>
                    Satir ekle
                  </button>
                  <button onClick={() => saveDemographics(kit.id)}>Demografiyi kaydet</button>
                </div>
                <strong style={{ fontSize: 14, display: "block", marginTop: 10 }}>Marka isbirlikleri</strong>
                {collabs.map((col, i) => (
                  <div key={col.id} style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 4, alignItems: "center" }}>
                    <input value={col.brandName} placeholder="marka *" style={{ width: 110 }}
                      onChange={(e) => setCollabs(collabs.map((x, j) => j === i ? { ...x, brandName: e.target.value } : x))} />
                    <input value={col.campaign ?? ""} placeholder="kampanya" style={{ width: 130 }}
                      onChange={(e) => setCollabs(collabs.map((x, j) => j === i ? { ...x, campaign: e.target.value } : x))} />
                    <input value={col.period ?? ""} placeholder="donem" style={{ width: 80 }}
                      onChange={(e) => setCollabs(collabs.map((x, j) => j === i ? { ...x, period: e.target.value } : x))} />
                    <input value={col.resultNote ?? ""} placeholder="sonuc" style={{ width: 150 }}
                      onChange={(e) => setCollabs(collabs.map((x, j) => j === i ? { ...x, resultNote: e.target.value } : x))} />
                    <button onClick={() => saveCollab(kit.id, col)}>Kaydet</button>
                    <button onClick={() => moveCollab(kit.id, i, -1)} disabled={i === 0}>↑</button>
                    <button onClick={() => moveCollab(kit.id, i, 1)} disabled={i === collabs.length - 1}>↓</button>
                    <button onClick={() => deleteCollab(kit.id, col.id)}>Sil</button>
                  </div>
                ))}
                <form onSubmit={(e) => addCollab(kit.id, e)}
                  style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
                  <input required placeholder="marka *" style={{ width: 110 }} value={collabForm.brandName}
                    onChange={(e) => setCollabForm({ ...collabForm, brandName: e.target.value })} />
                  <input placeholder="kampanya" style={{ width: 130 }} value={collabForm.campaign}
                    onChange={(e) => setCollabForm({ ...collabForm, campaign: e.target.value })} />
                  <input placeholder="donem" style={{ width: 80 }} value={collabForm.period}
                    onChange={(e) => setCollabForm({ ...collabForm, period: e.target.value })} />
                  <input placeholder="sonuc" style={{ width: 150 }} value={collabForm.resultNote}
                    onChange={(e) => setCollabForm({ ...collabForm, resultNote: e.target.value })} />
                  <button type="submit">Isbirligi ekle</button>
                </form>

                <small style={{ color: "#666" }}>
                  Not: degisiklikler public sayfaya ancak Yayinla ile yansir.
                </small>
              </div>
            )}
            {versionsFor === kit.id && (
              <div style={{ marginTop: 8, borderTop: "1px dashed #ccc", paddingTop: 8 }}>
                {versions.length === 0 && <small>Henuz yayinlanmamis.</small>}
                {versions.map((v) => (
                  <div key={v.version} style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    <small>
                      v{v.version} — /{v.slug} — {new Date(v.publishedAt).toLocaleString("tr-TR")}
                      {v.active && <strong> (yayinda)</strong>}
                    </small>
                    {!v.active && (
                      <button onClick={() => activateVersion(kit.id, v.version)}>
                        Bu versiyona don
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      ))}
    </main>
  );
}
