import type { PublicKit } from "./KitCard";

// Shared by the page AND the opengraph-image route so both read the same
// Data Cache entry: one tag, one schema version, one revalidation moment.
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

// Part of the fetch cache key. The Data Cache survives deployments, so cached
// entries with the OLD response shape would break the new page; bumping this
// whenever the public payload shape changes starts from a clean cache.
export const PUBLIC_SCHEMA_VERSION = "6";

export async function getKit(slug: string): Promise<PublicKit | null> {
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
  const kit = (await res.json()) as PublicKit;
  if (!kit?.title) throw new Error("Unexpected kit payload");
  return kit;
}
