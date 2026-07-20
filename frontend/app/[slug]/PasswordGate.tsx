"use client";

import { useState } from "react";
import { Lock } from "lucide-react";
import KitCard, { PublicKit } from "./KitCard";
import { Button, Card, Input } from "@/app/_components/ui";

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

  if (kit) return <KitCard kit={kit} />;

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
    <div data-theme={theme === "dark" ? "dark" : "light"}>
      <main className="grid min-h-screen place-items-center bg-page px-5 text-fg">
        <Card className="w-full max-w-sm p-7 text-center">
          <div className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-brand-weak text-brand">
            <Lock className="h-5 w-5" />
          </div>
          <h1 className="mt-4 text-lg font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-muted">
            Bu medya kiti sifre korumali. Goruntulemek icin sifreyi girin.
          </p>
          <form onSubmit={submit} className="mt-5 grid gap-2.5">
            <Input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Sifre"
              autoFocus
              required
            />
            <Button type="submit" disabled={busy} className="w-full">
              {busy ? "Kontrol ediliyor..." : "Goruntule"}
            </Button>
          </form>
          {error && <p className="mt-3 text-sm text-danger">{error}</p>}
        </Card>
      </main>
    </div>
  );
}
