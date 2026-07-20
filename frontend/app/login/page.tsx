"use client";

import { useState } from "react";
import Link from "next/link";
import { Sparkles } from "lucide-react";
import { Button, Card, Input, Label } from "@/app/_components/ui";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

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
    <main className="grid min-h-screen place-items-center px-6 py-12">
      <div className="w-full max-w-sm">
        <Link href="/" className="mb-6 flex items-center justify-center gap-2">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-strong text-sm font-bold text-white">
            LM
          </span>
          <span className="font-semibold tracking-tight">LocalMediaKit</span>
        </Link>

        <Card className="p-6">
          <h1 className="text-xl font-semibold tracking-tight">Giris yap</h1>
          <p className="mt-1 text-sm text-muted">Panonuza erisin.</p>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              login(email, password);
            }}
            className="mt-5 grid gap-4"
          >
            <div className="grid gap-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="siz@ornek.com" value={email}
                onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="password">Sifre</Label>
              <Input id="password" type="password" placeholder="••••••••" value={password}
                onChange={(e) => setPassword(e.target.value)} required />
            </div>
            <Button type="submit" disabled={busy} className="w-full">
              {busy ? "..." : "Giris yap"}
            </Button>
          </form>

          <div className="my-5 flex items-center gap-3 text-xs text-faint">
            <span className="h-px flex-1 bg-line" /> veya <span className="h-px flex-1 bg-line" />
          </div>

          <Button variant="secondary" className="w-full" disabled={busy}
            onClick={() => login(DEMO_EMAIL, DEMO_PASSWORD)}>
            <Sparkles className="h-4 w-4 text-brand" />
            Demo olarak gez
          </Button>
          <p className="mt-2 text-center text-xs text-faint">
            Dolu bir PRO hesabiyla panoyu kesfedin (gece sifirlanir).
          </p>

          {error && <p className="mt-4 text-sm text-danger">{error}</p>}
        </Card>

        <p className="mt-4 text-center text-sm text-muted">
          Hesabin yok mu?{" "}
          <Link href="/register" className="font-medium text-brand hover:underline">
            Kayit ol
          </Link>
        </p>
      </div>
    </main>
  );
}
