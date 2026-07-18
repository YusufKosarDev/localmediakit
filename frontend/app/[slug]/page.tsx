import type { Metadata } from "next";
import { notFound } from "next/navigation";
import TrackView from "./TrackView";

// On-demand ISR only: the page is generated on first visit, cached at the edge,
// and re-generated ONLY when the backend triggers revalidateTag("kit-<slug>")
// after a publish. Visitors never wait for (or depend on) the backend. Stats
// and demographics come frozen inside the published snapshot — nothing on this
// page is computed live.
export const dynamicParams = true;
export const dynamic = "force-static";

export async function generateStaticParams() {
  return [];
}

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

// Part of the fetch cache key. The Data Cache survives deployments, so cached
// entries with the OLD response shape would break the new page; bumping this
// whenever the public payload shape changes starts from a clean cache.
const PUBLIC_SCHEMA_VERSION = "4";

type PlatformStat = {
  platform: string;
  followers: number;
  avgViews: number | null;
  avgLikes: number | null;
  avgComments: number | null;
  engagementRate: number | null;
  followerGrowth30d: number | null;
};

type Demographic = {
  category: string;
  label: string;
  percentage: number;
};

type Collaboration = {
  brandName: string;
  campaign: string | null;
  period: string | null;
  resultNote: string | null;
  logoUrl: string | null;
};

type PublicKit = {
  slug: string;
  title: string;
  headline: string | null;
  avatarUrl: string | null;
  theme: string;
  displayName: string;
  platforms: PlatformStat[];
  demographics: Demographic[];
  collaborations: Collaboration[];
  showBadge: boolean;
  version: number;
  publishedAt: string;
};

const PLATFORM_NAMES: Record<string, string> = {
  YOUTUBE: "YouTube",
  INSTAGRAM: "Instagram",
  TIKTOK: "TikTok",
};

const CATEGORY_NAMES: Record<string, string> = {
  AGE: "Yas",
  GENDER: "Cinsiyet",
  COUNTRY: "Ulke",
};

const compact = new Intl.NumberFormat("tr-TR", {
  notation: "compact",
  maximumFractionDigits: 1,
});

async function getKit(slug: string): Promise<PublicKit | null> {
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
  const c = dark
    ? {
        bg: "#0e1116",
        card: "#161b22",
        text: "#e6edf3",
        muted: "#8b949e",
        line: "#21262d",
        chip: "#21262d",
        bar: "#2f81f7",
        barBg: "#21262d",
        up: "#2ea043",
        down: "#f85149",
      }
    : {
        bg: "#f6f7f9",
        card: "#ffffff",
        text: "#1a1f27",
        muted: "#6b7280",
        line: "#e5e7eb",
        chip: "#f1f3f6",
        bar: "#2563eb",
        barBg: "#eef1f5",
        up: "#15803d",
        down: "#b91c1c",
      };

  const publishedDate = new Date(kit.publishedAt).toLocaleDateString("tr-TR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  const demographicsByCategory = ["AGE", "GENDER", "COUNTRY"]
    .map((category) => ({
      category,
      entries: kit.demographics.filter((d) => d.category === category),
    }))
    .filter((group) => group.entries.length > 0);

  return (
    <main
      style={{
        minHeight: "100vh",
        background: c.bg,
        color: c.text,
        fontFamily:
          "system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "48px 16px",
      }}
    >
      <TrackView slug={kit.slug} />
      <article
        style={{
          background: c.card,
          border: `1px solid ${c.line}`,
          borderRadius: 16,
          maxWidth: 640,
          width: "100%",
          padding: "48px 40px",
          boxShadow: dark ? "none" : "0 8px 30px rgba(0,0,0,0.06)",
        }}
      >
        <header style={{ textAlign: "center" }}>
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
                border: `1px solid ${c.line}`,
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
                background: c.chip,
                color: c.muted,
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
              color: c.muted,
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
                maxWidth: 460,
                fontSize: 17,
                lineHeight: 1.6,
                color: c.muted,
              }}
            >
              {kit.headline}
            </p>
          )}
        </header>

        {kit.platforms.length > 0 && (
          <section style={{ marginTop: 36 }}>
            <h2
              style={{
                fontSize: 13,
                letterSpacing: "0.12em",
                textTransform: "uppercase",
                color: c.muted,
                margin: "0 0 12px",
              }}
            >
              Platformlar
            </h2>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
                gap: 12,
              }}
            >
              {kit.platforms.map((p) => (
                <div
                  key={p.platform}
                  style={{
                    border: `1px solid ${c.line}`,
                    borderRadius: 12,
                    padding: "16px 14px",
                  }}
                >
                  <div style={{ fontSize: 14, fontWeight: 600 }}>
                    {PLATFORM_NAMES[p.platform] ?? p.platform}
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 700, marginTop: 6 }}>
                    {compact.format(p.followers)}
                  </div>
                  <div style={{ fontSize: 12, color: c.muted }}>takipci</div>
                  <div style={{ marginTop: 10, fontSize: 13 }}>
                    {p.engagementRate != null && (
                      <div>
                        <strong>%{p.engagementRate.toLocaleString("tr-TR")}</strong>{" "}
                        <span style={{ color: c.muted }}>etkilesim</span>
                      </div>
                    )}
                    {p.followerGrowth30d != null && (
                      <div
                        style={{
                          display: "inline-block",
                          marginTop: 6,
                          padding: "2px 8px",
                          borderRadius: 999,
                          fontSize: 12,
                          fontWeight: 600,
                          background: c.chip,
                          color: p.followerGrowth30d >= 0 ? c.up : c.down,
                        }}
                      >
                        {p.followerGrowth30d >= 0 ? "+" : ""}
                        {p.followerGrowth30d.toLocaleString("tr-TR")}% · 30 gun
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {demographicsByCategory.length > 0 && (
          <section style={{ marginTop: 32 }}>
            <h2
              style={{
                fontSize: 13,
                letterSpacing: "0.12em",
                textTransform: "uppercase",
                color: c.muted,
                margin: "0 0 12px",
              }}
            >
              Kitle
            </h2>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
                gap: 16,
              }}
            >
              {demographicsByCategory.map((group) => (
                <div key={group.category}>
                  <div
                    style={{
                      fontSize: 13,
                      fontWeight: 600,
                      marginBottom: 8,
                      color: c.muted,
                    }}
                  >
                    {CATEGORY_NAMES[group.category] ?? group.category}
                  </div>
                  {group.entries.map((d) => (
                    <div key={d.label} style={{ marginBottom: 8 }}>
                      <div
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          fontSize: 13,
                          marginBottom: 3,
                        }}
                      >
                        <span>{d.label}</span>
                        <span style={{ color: c.muted }}>
                          %{d.percentage.toLocaleString("tr-TR")}
                        </span>
                      </div>
                      <div
                        style={{
                          height: 6,
                          borderRadius: 999,
                          background: c.barBg,
                          overflow: "hidden",
                        }}
                      >
                        <div
                          style={{
                            width: `${Math.min(d.percentage, 100)}%`,
                            height: "100%",
                            borderRadius: 999,
                            background: c.bar,
                          }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </section>
        )}

        {kit.collaborations.length > 0 && (
          <section style={{ marginTop: 32 }}>
            <h2
              style={{
                fontSize: 13,
                letterSpacing: "0.12em",
                textTransform: "uppercase",
                color: c.muted,
                margin: "0 0 12px",
              }}
            >
              Marka Isbirlikleri
            </h2>
            <div style={{ display: "grid", gap: 10 }}>
              {kit.collaborations.map((col, i) => (
                <div
                  key={i}
                  style={{
                    border: `1px solid ${c.line}`,
                    borderRadius: 12,
                    padding: "14px 16px",
                    display: "flex",
                    gap: 12,
                    alignItems: "center",
                  }}
                >
                  {col.logoUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={col.logoUrl}
                      alt={col.brandName}
                      width={40}
                      height={40}
                      style={{
                        width: 40,
                        height: 40,
                        borderRadius: 8,
                        objectFit: "cover",
                        border: `1px solid ${c.line}`,
                        flexShrink: 0,
                      }}
                    />
                  ) : (
                    <div
                      aria-hidden
                      style={{
                        width: 40,
                        height: 40,
                        borderRadius: 8,
                        flexShrink: 0,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        fontWeight: 600,
                        background: c.chip,
                        color: c.muted,
                      }}
                    >
                      {col.brandName.charAt(0).toUpperCase()}
                    </div>
                  )}
                  <div style={{ minWidth: 0 }}>
                    <div
                      style={{
                        display: "flex",
                        gap: 8,
                        alignItems: "baseline",
                        flexWrap: "wrap",
                      }}
                    >
                      <strong style={{ fontSize: 15 }}>{col.brandName}</strong>
                      {col.period && (
                        <span style={{ fontSize: 12, color: c.muted }}>
                          {col.period}
                        </span>
                      )}
                    </div>
                    {col.campaign && (
                      <div style={{ fontSize: 14, marginTop: 2 }}>{col.campaign}</div>
                    )}
                    {col.resultNote && (
                      <div style={{ fontSize: 13, color: c.muted, marginTop: 2 }}>
                        {col.resultNote}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        <footer
          style={{
            marginTop: 36,
            paddingTop: 20,
            borderTop: `1px solid ${c.line}`,
            fontSize: 12,
            color: c.muted,
            textAlign: "center",
          }}
        >
          {publishedDate} tarihinde yayinlandi
          {kit.showBadge !== false && " · LocalMediaKit"}
        </footer>
      </article>
    </main>
  );
}
