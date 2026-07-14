"use client";

import { useEffect, useState } from "react";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

type Me = { id: number; email: string; displayName: string; plan: string };

export default function DashboardPage() {
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (!token) {
      setError("Oturum yok.");
      return;
    }
    fetch(`${BACKEND}/api/me`, { headers: { Authorization: `Bearer ${token}` } })
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((data: Me) => setMe(data))
      .catch(() => setError("Oturum gecersiz veya suresi dolmus."));
  }, []);

  function logout() {
    localStorage.removeItem("token");
    window.location.href = "/login";
  }

  return (
    <main style={{ fontFamily: "system-ui, sans-serif", padding: 40 }}>
      <h1>Panel</h1>
      {me ? (
        <div>
          <p>Hos geldin, <strong>{me.displayName}</strong></p>
          <ul>
            <li>email: {me.email}</li>
            <li>plan: {me.plan}</li>
          </ul>
          <button onClick={logout}>Cikis</button>
        </div>
      ) : (
        <div>
          <p style={{ color: "crimson" }}>{error || "Yukleniyor..."}</p>
          <p><a href="/login">Giris yap</a></p>
        </div>
      )}
    </main>
  );
}
