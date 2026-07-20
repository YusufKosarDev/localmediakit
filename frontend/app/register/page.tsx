"use client";

import { useState } from "react";
import Link from "next/link";
import { Button, Card, Input, Label } from "@/app/_components/ui";

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
      if (res.status === 429) {
        setError("Cok fazla deneme. Lutfen biraz bekleyin.");
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
    <main className="grid min-h-screen place-items-center px-6 py-12">
      <div className="w-full max-w-sm">
        <Link href="/" className="mb-6 flex items-center justify-center gap-2">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-strong text-sm font-bold text-white">
            LM
          </span>
          <span className="font-semibold tracking-tight">LocalMediaKit</span>
        </Link>

        <Card className="p-6">
          <h1 className="text-xl font-semibold tracking-tight">Hesap olustur</h1>
          <p className="mt-1 text-sm text-muted">Ilk medya kitinizi dakikalar icinde yayinlayin.</p>

          <form onSubmit={handleSubmit} className="mt-5 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="name">Ad</Label>
              <Input id="name" placeholder="Adiniz" value={displayName}
                onChange={(e) => setDisplayName(e.target.value)} required />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="siz@ornek.com" value={email}
                onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="password">Sifre</Label>
              <Input id="password" type="password" placeholder="En az 8 karakter" value={password}
                onChange={(e) => setPassword(e.target.value)} required />
            </div>
            <Button type="submit" disabled={busy} className="w-full">
              {busy ? "..." : "Kayit ol"}
            </Button>
          </form>

          {error && <p className="mt-4 text-sm text-danger">{error}</p>}
        </Card>

        <p className="mt-4 text-center text-sm text-muted">
          Zaten hesabin var mi?{" "}
          <Link href="/login" className="font-medium text-brand hover:underline">
            Giris yap
          </Link>
        </p>
      </div>
    </main>
  );
}
