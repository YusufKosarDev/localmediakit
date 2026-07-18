import type { Metadata } from "next";
import { notFound } from "next/navigation";

// On-demand ISR only: the page is generated on first visit, cached at the edge,
// and re-generated ONLY when the backend triggers revalidateTag("kit-<slug>")
// after a publish. Visitors never wait for (or depend on) the backend.
export const dynamicParams = true;
export const dynamic = "force-static";

export async function generateStaticParams() {
  return [];
}

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

// Part of the fetch cache key. The Data Cache survives deployments, so cached
// entries with the OLD response shape would break the new page; bumping this
// whenever the public payload shape changes starts from a clean cache.
const PUBLIC_SCHEMA_VERSION = "2";

type PublicKit = {
  slug: string;
  title: string;
  headline: string | null;
  avatarUrl: string | null;
  theme: string;
  displayName: string;
  version: number;
  publishedAt: string;
};

async function getKit(slug: string): Promise<PublicKit | null> {
  const res = await fetch(`${BACKEND_URL}/api/public/kits/${slug}?schema=${PUBLIC_SCHEMA_VERSION}`, {
    // force-cache + tag => stored in the Data Cache, invalidated on demand.
    cache: "force-cache",
    next: { tags: [`kit-${slug}`] },
  });
  if (res.status === 404) return null;
  // Any other failure (backend asleep on a cold, uncached slug) throws into
  // the error boundary; the error result is NOT cached, so the next visit retries.
  if (!res.ok) throw new Error(`Backend responded ${res.status}`);
  const kit = (await res.json()) as PublicKit;
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

  const dark = kit.theme === "dark";
  const colors = dark
    ? { bg: "#0e1116", card: "#161b22", text: "#e6edf3", muted: "#8b949e", line: "#21262d" }
    : { bg: "#f6f7f9", card: "#ffffff", text: "#1a1f27", muted: "#6b7280", line: "#e5e7eb" };

  const publishedDate = new Date(kit.publishedAt).toLocaleDateString("tr-TR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  return (
    <main
      style={{
        minHeight: "100vh",
        background: colors.bg,
        color: colors.text,
        fontFamily:
          "system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "48px 16px",
      }}
    >
      <article
        style={{
          background: colors.card,
          border: `1px solid ${colors.line}`,
          borderRadius: 16,
          maxWidth: 560,
          width: "100%",
          padding: "48px 40px",
          textAlign: "center",
          boxShadow: dark ? "none" : "0 8px 30px rgba(0,0,0,0.06)",
        }}
      >
        {kit.avatarUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={kit.avatarUrl}
            alt={kit.displayName}
            width={96}
            height={96}
            style={{
              width: 96,
              height: 96,
              borderRadius: "50%",
              objectFit: "cover",
              border: `1px solid ${colors.line}`,
              marginBottom: 20,
            }}
          />
        ) : (
          <div
            aria-hidden
            style={{
              width: 96,
              height: 96,
              borderRadius: "50%",
              margin: "0 auto 20px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 36,
              fontWeight: 600,
              background: dark ? "#21262d" : "#eef1f5",
              color: colors.muted,
            }}
          >
            {kit.displayName.charAt(0).toUpperCase()}
          </div>
        )}

        <p
          style={{
            margin: "0 0 8px",
            fontSize: 13,
            letterSpacing: "0.12em",
            textTransform: "uppercase",
            color: colors.muted,
          }}
        >
          {kit.displayName}
        </p>
        <h1 style={{ margin: "0 0 12px", fontSize: 32, lineHeight: 1.2 }}>
          {kit.title}
        </h1>
        {kit.headline && (
          <p
            style={{
              margin: "0 auto",
              maxWidth: 420,
              fontSize: 17,
              lineHeight: 1.6,
              color: colors.muted,
            }}
          >
            {kit.headline}
          </p>
        )}

        <footer
          style={{
            marginTop: 36,
            paddingTop: 20,
            borderTop: `1px solid ${colors.line}`,
            fontSize: 12,
            color: colors.muted,
          }}
        >
          {publishedDate} tarihinde yayinlandi · LocalMediaKit
        </footer>
      </article>
    </main>
  );
}
