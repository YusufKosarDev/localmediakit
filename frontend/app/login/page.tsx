"use client";

import { useState } from "react";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

// Public demo credentials — the demo account is meant to be shared and is reset
// nightly on the backend.
const DEMO_EMAIL = "demo@localmediakit.app";
const DEMO_PASSWORD = "demo1234";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function login(mail: string, pass: string) {
    setError("");
    setBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: mail, password: pass }),
      });
      if (!res.ok) {
        setError(
          res.status === 429
            ? "Cok fazla deneme. Lutfen biraz bekleyin."
            : "Giris basarisiz (email/sifre hatali)."
        );
        return;
      }
      const data = await res.json();
      localStorage.setItem("token", data.token);
      window.location.href = "/dashboard";
    } catch {
      setError("Sunucuya ulasilamadi.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main style={{ fontFamily: "system-ui, sans-serif", padding: 40, maxWidth: 360 }}>
      <h1>Giris</h1>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          login(email, password);
        }}
        style={{ display: "grid", gap: 10 }}
      >
        <input type="email" placeholder="email" value={email}
          onChange={(e) => setEmail(e.target.value)} required />
        <input type="password" placeholder="sifre" value={password}
          onChange={(e) => setPassword(e.target.value)} required />
        <button type="submit" disabled={busy}>{busy ? "..." : "Giris yap"}</button>
      </form>

      <div style={{ margin: "16px 0", borderTop: "1px solid #e5e7eb", paddingTop: 16 }}>
        <button
          onClick={() => login(DEMO_EMAIL, DEMO_PASSWORD)}
          disabled={busy}
          style={{ fontWeight: 600, padding: "8px 14px", cursor: busy ? "default" : "pointer" }}
        >
          Demo olarak gez
        </button>
        <p style={{ fontSize: 12, color: "#6b7280", marginTop: 6 }}>
          Dolu bir PRO hesapla panoyu kesfedin (gece sifirlanir).
        </p>
      </div>

      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <p>Hesabin yok mu? <a href="/register">Kayit ol</a></p>
    </main>
  );
}
