"use client";

import { useState } from "react";
import { Send } from "lucide-react";
import { Button, Card, Input } from "@/app/_components/ui";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

// Brand contact form on the public page. The page stays static/edge-cached:
// this is a client component that POSTs straight to the backend. The endpoint
// always answers 202, so the UI always shows success — whether the submission
// was stored or dropped (spam, honeypot, disabled kit) is deliberately opaque.
export default function ContactForm({ slug }: { slug: string }) {
  const [form, setForm] = useState({ brandName: "", email: "", message: "" });
  const [website, setWebsite] = useState(""); // honeypot: humans never see it
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await fetch(`${BACKEND}/api/public/kits/${slug}/contact`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, website: website || null }),
      });
      if (res.status === 202) setSent(true);
      else if (res.status === 429) setError("Cok fazla istek. Lutfen birkac dakika sonra tekrar deneyin.");
      else setError(`Gonderilemedi (HTTP ${res.status}).`);
    } catch {
      setError("Baglanti hatasi. Tekrar deneyin.");
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <Card className="p-5 text-center">
        <p className="font-medium">Teklifiniz iletildi.</p>
        <p className="mt-1 text-sm text-muted">Uretici en kisa surede sizinle iletisime gececek.</p>
      </Card>
    );
  }

  return (
    <Card className="no-print p-5">
      <p className="mb-3 text-sm text-muted">Bu uretici ile calismak ister misiniz? Teklifinizi iletin.</p>
      <form onSubmit={submit} className="grid gap-2.5">
        <div className="grid gap-2.5 sm:grid-cols-2">
          <Input
            required
            maxLength={100}
            placeholder="Marka / sirket adi *"
            value={form.brandName}
            onChange={(e) => setForm({ ...form, brandName: e.target.value })}
          />
          <Input
            required
            type="email"
            maxLength={255}
            placeholder="E-posta *"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
        </div>
        <textarea
          required
          maxLength={2000}
          rows={4}
          placeholder="Mesajiniz *"
          value={form.message}
          onChange={(e) => setForm({ ...form, message: e.target.value })}
          className="w-full rounded-xl border border-line bg-surface px-3.5 py-2.5 text-sm text-fg placeholder:text-faint focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20"
        />
        {/* Honeypot: visually hidden, tab-skipped; bots that fill it are dropped. */}
        <input
          type="text"
          name="website"
          value={website}
          onChange={(e) => setWebsite(e.target.value)}
          tabIndex={-1}
          autoComplete="off"
          aria-hidden="true"
          className="absolute -left-[9999px] h-0 w-0 opacity-0"
        />
        <Button type="submit" disabled={busy} className="justify-self-start">
          <Send className="h-4 w-4" /> {busy ? "Gonderiliyor..." : "Teklif gonder"}
        </Button>
      </form>
      {error && <p className="mt-3 text-sm text-danger">{error}</p>}
    </Card>
  );
}
