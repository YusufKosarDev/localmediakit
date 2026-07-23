import type { Metadata } from "next";
import Link from "next/link";
import KitCard, { PublicKit } from "@/app/[slug]/KitCard";

// Draft preview: the deliberate inverse of the public [slug] page. That one is
// force-static and edge-cached from an immutable snapshot; this one is fully
// dynamic, fetched per-request with no-store, and renders the LIVE draft that
// the short-lived signed token authorizes. Never indexed, never cached.
export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Onizleme — LocalMediaKit",
  robots: { index: false, follow: false },
};

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export default async function PreviewPage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = await params;
  let kit: PublicKit | null = null;
  try {
    const res = await fetch(`${BACKEND_URL}/api/public/preview/${token}`, {
      cache: "no-store",
    });
    if (res.ok) kit = (await res.json()) as PublicKit;
  } catch {
    // Backend unreachable: fall through to the friendly message below.
  }

  if (!kit) {
    return (
      <main className="grid min-h-screen place-items-center bg-page px-6 text-center text-fg">
        <div>
          <h1 className="text-lg font-semibold tracking-tight">Onizleme acilamadi</h1>
          <p className="mt-2 max-w-sm text-sm text-muted">
            Bu onizleme linki gecersiz veya suresi dolmus olabilir. Panodan yeni
            bir onizleme linki olusturabilirsiniz.
          </p>
          <Link href="/dashboard" className="mt-4 inline-block font-medium text-brand hover:underline">
            Panoya don
          </Link>
        </div>
      </main>
    );
  }

  return <KitCard kit={kit} preview />;
}
