"use client";

import { useState } from "react";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        setError("Giris basarisiz (email/sifre hatali).");
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
      <form onSubmit={handleSubmit} style={{ display: "grid", gap: 10 }}>
        <input type="email" placeholder="email" value={email}
          onChange={(e) => setEmail(e.target.value)} required />
        <input type="password" placeholder="sifre" value={password}
          onChange={(e) => setPassword(e.target.value)} required />
        <button type="submit" disabled={busy}>{busy ? "..." : "Giris yap"}</button>
      </form>
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <p>Hesabin yok mu? <a href="/register">Kayit ol</a></p>
    </main>
  );
}
