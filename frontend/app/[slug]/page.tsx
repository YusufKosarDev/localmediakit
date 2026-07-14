import { notFound } from "next/navigation";

// On-demand ISR only: the page is generated on first visit, cached at the edge,
// and re-generated ONLY when the backend triggers revalidateTag("kit-<slug>").
// No time-based revalidation, no per-request backend dependency.
export const dynamicParams = true;
// force-static: the route is rendered statically and stored in the Full Route
// Cache. Each slug is generated on its FIRST visit, then served statically from
// the edge (no per-request render, no backend call), and re-generated only on
// demand when the backend calls revalidateTag("kit-<slug>").
export const dynamic = "force-static";

export async function generateStaticParams() {
  return [];
}

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

type Kit = { slug: string; content: string; updatedAt: string };

async function getKit(slug: string): Promise<Kit | null> {
  const res = await fetch(`${BACKEND_URL}/api/public/kits/${slug}`, {
    // force-cache + tag => stored in the Data Cache, invalidated on demand.
    cache: "force-cache",
    next: { tags: [`kit-${slug}`] },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Backend responded ${res.status}`);
  return res.json();
}

export default async function KitPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const kit = await getKit(slug);
  if (!kit) notFound();

  return (
    <main style={{ fontFamily: "system-ui, sans-serif", padding: 40 }}>
      <h1>Medya Kiti: {kit.slug}</h1>
      <p style={{ fontSize: 20 }}>{kit.content}</p>
      <hr />
      <small>icerik guncellendi (backend): {kit.updatedAt}</small>
    </main>
  );
}
