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
};

function authHeaders(): HeadersInit {
  const token = typeof window !== "undefined" ? localStorage.getItem("token") : null;
  return { "Content-Type": "application/json", Authorization: `Bearer ${token ?? ""}` };
}

export default function DashboardPage() {
  const [me, setMe] = useState<Me | null>(null);
  const [kits, setKits] = useState<Kit[]>([]);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ title: "", headline: "", avatarUrl: "", theme: "light", slug: "" });

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
          <div><small>#{kit.id} — durum: {kit.status}</small></div>
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
              <button onClick={() => deleteKit(kit.id)}>Sil</button>
            </div>
          </div>
        </div>
      ))}
    </main>
  );
}
