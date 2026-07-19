"use client";

import { useState } from "react";
import KitCard, { PublicKit } from "./KitCard";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

// Client-side unlock for a protected kit. The static page ships only this gate
// (no sensitive data reaches the edge); the content is fetched per-request from
// the backend after the password is verified, then rendered with the shared
// KitCard — identical to a public kit once unlocked.
export default function PasswordGate({
  slug,
  title,
  theme,
}: {
  slug: string;
  title: string;
  theme: string;
}) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [kit, setKit] = useState<PublicKit | null>(null);

  const dark = theme === "dark";
  const colors = dark
    ? { bg: "#0e1116", card: "#161b22", text: "#e6edf3", muted: "#8b949e", line: "#21262d" }
    : { bg: "#f6f7f9", card: "#ffffff", text: "#1a1f27", muted: "#6b7280", line: "#e5e7eb" };

  if (kit) {
    return <KitCard kit={kit} />;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/public/kits/${slug}/unlock`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password }),
      });
      if (res.ok) {
        setKit(await res.json());
      } else if (res.status === 401) {
        setError("Sifre yanlis.");
      } else if (res.status === 429) {
        setError("Cok fazla deneme. Lutfen birkac dakika sonra tekrar deneyin.");
      } else {
        setError(`Acilamadi (HTTP ${res.status}).`);
      }
    } catch {
      setError("Baglanti hatasi. Tekrar deneyin.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main
      style={{
        minHeight: "100vh",
        background: colors.bg,
        color: colors.text,
        fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "48px 16px",
      }}
    >
      <div
        style={{
          background: colors.card,
          border: `1px solid ${colors.line}`,
          borderRadius: 16,
          maxWidth: 400,
          width: "100%",
          padding: "40px 32px",
          textAlign: "center",
          boxShadow: dark ? "none" : "0 8px 30px rgba(0,0,0,0.06)",
        }}
      >
        <div style={{ fontSize: 44 }} aria-hidden>
          🔒
        </div>
        <h1 style={{ fontSize: 22, margin: "12px 0 6px" }}>{title}</h1>
        <p style={{ color: colors.muted, fontSize: 14, marginBottom: 20 }}>
          Bu medya kiti sifre korumali. Goruntulemek icin sifreyi girin.
        </p>
        <form onSubmit={submit} style={{ display: "grid", gap: 10 }}>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Sifre"
            autoFocus
            required
            style={{
              padding: "10px 12px",
              borderRadius: 8,
              border: `1px solid ${colors.line}`,
              background: dark ? "#0e1116" : "#fff",
              color: colors.text,
              fontSize: 15,
            }}
          />
          <button
            type="submit"
            disabled={busy}
            style={{
              padding: "10px 12px",
              borderRadius: 8,
              border: "none",
              background: "#2563eb",
              color: "#fff",
              fontSize: 15,
              fontWeight: 600,
              cursor: busy ? "default" : "pointer",
              opacity: busy ? 0.7 : 1,
            }}
          >
            {busy ? "Kontrol ediliyor..." : "Goruntule"}
          </button>
        </form>
        {error && (
          <p style={{ color: "#dc2626", fontSize: 14, marginTop: 12 }}>{error}</p>
        )}
      </div>
    </main>
  );
}
