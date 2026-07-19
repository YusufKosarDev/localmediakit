import type { Metadata } from "next";
import { notFound } from "next/navigation";
import KitCard, { PublicKit } from "./KitCard";
import PasswordGate from "./PasswordGate";

// On-demand ISR only: the page is generated on first visit, cached at the edge,
// and re-generated ONLY when the backend triggers revalidateTag("kit-<slug>")
// after a publish. Public kits ship their full (frozen) content this way.
//
// Protected kits ship ONLY the gate here — the backend returns just
// {isProtected:true, slug, title, theme}, so no sensitive data ever reaches the
// edge cache. Their content is fetched per-request through the unlock endpoint
// (see PasswordGate). Normal kits are completely unchanged: still edge HIT.
export const dynamicParams = true;
export const dynamic = "force-static";

export async function generateStaticParams() {
  return [];
}

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

// Part of the fetch cache key. The Data Cache survives deployments, so cached
// entries with the OLD response shape would break the new page; bumping this
// whenever the public payload shape changes starts from a clean cache.
const PUBLIC_SCHEMA_VERSION = "5";

// The gate payload for a protected kit (subset of PublicKit).
type KitMeta = PublicKit;

async function getKit(slug: string): Promise<KitMeta | null> {
  const res = await fetch(
    `${BACKEND_URL}/api/public/kits/${slug}?schema=${PUBLIC_SCHEMA_VERSION}`,
    {
      // force-cache + tag => stored in the Data Cache, invalidated on demand.
      cache: "force-cache",
      next: { tags: [`kit-${slug}`] },
    }
  );
  if (res.status === 404) return null;
  // Any other failure (backend asleep on a cold, uncached slug) throws into
  // the error boundary; the error result is NOT cached, so the next visit retries.
  if (!res.ok) throw new Error(`Backend responded ${res.status}`);
  const kit = (await res.json()) as KitMeta;
  if (!kit?.title) throw new Error("Unexpected kit payload");
  return kit;
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const kit = await getKit(slug);
  if (!kit) return { title: "Sayfa bulunamadi" };
  // A protected kit exposes only its title in metadata; nothing sensitive.
  if (kit.isProtected) {
    return { title: `${kit.title} — Sifre korumali`, robots: { index: false } };
  }
  const description = kit.headline ?? `${kit.displayName} medya kiti`;
  return {
    title: `${kit.title} — ${kit.displayName}`,
    description,
    openGraph: {
      title: kit.title,
      description,
      type: "profile",
      ...(kit.avatarUrl ? { images: [{ url: kit.avatarUrl }] } : {}),
    },
  };
}

export default async function KitPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const kit = await getKit(slug);
  if (!kit) notFound();

  if (kit.isProtected) {
    return <PasswordGate slug={kit.slug} title={kit.title} theme={kit.theme} />;
  }
  return <KitCard kit={kit} />;
}
