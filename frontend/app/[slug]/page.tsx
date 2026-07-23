import type { Metadata } from "next";
import { notFound } from "next/navigation";
import KitCard from "./KitCard";
import PasswordGate from "./PasswordGate";
import { getKit } from "./kit-data";

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

// Fetching lives in kit-data.ts, shared with opengraph-image.tsx so the page
// and its social image always read (and revalidate) the same cached snapshot.

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
  // og:image / twitter:image come from the opengraph-image.tsx file convention
  // (a generated, kit-specific card) — not set here so the convention wins.
  return {
    title: `${kit.title} — ${kit.displayName}`,
    description,
    openGraph: {
      title: `${kit.title} — ${kit.displayName}`,
      description,
      type: "profile",
    },
    twitter: {
      card: "summary_large_image",
      title: `${kit.title} — ${kit.displayName}`,
      description,
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
