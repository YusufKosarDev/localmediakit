"use client";

import { useState } from "react";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, displayName }),
      });
      if (res.status === 409) {
        setError("Bu email zaten kayitli.");
        return;
      }
      if (res.status === 400) {
        setError("Gecersiz bilgi (sifre en az 8 karakter, gecerli email).");
        return;
      }
      if (!res.ok) {
        setError("Kayit basarisiz.");
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
      <h1>Kayit ol</h1>
      <form onSubmit={handleSubmit} style={{ display: "grid", gap: 10 }}>
        <input placeholder="ad" value={displayName}
          onChange={(e) => setDisplayName(e.target.value)} required />
        <input type="email" placeholder="email" value={email}
          onChange={(e) => setEmail(e.target.value)} required />
        <input type="password" placeholder="sifre (min 8)" value={password}
          onChange={(e) => setPassword(e.target.value)} required />
        <button type="submit" disabled={busy}>{busy ? "..." : "Kayit ol"}</button>
      </form>
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <p>Zaten hesabin var mi? <a href="/login">Giris yap</a></p>
    </main>
  );
}
