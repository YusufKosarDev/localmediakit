import { ImageResponse } from "next/og";
import { getKit } from "./kit-data";

// Generated social card for a kit. Reads the SAME cached fetch (same tag) as
// the page, so a publish revalidates page and image together, and rendering
// never wakes a sleeping backend once the snapshot is in the Data Cache.
// The frozen showBadge rule applies here too: FREE cards carry the wordmark.
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";
export const alt = "Medya kiti";

// Mirrors the design tokens in globals.css (satori cannot read CSS variables).
const THEME = {
  light: { page: "#f6f6f8", surface: "#ffffff", fg: "#0b0b0f", muted: "#62636b", line: "#e8e8ec", brand: "#6d40e6", brandWeak: "#f2ecfe" },
  dark: { page: "#0a0a0c", surface: "#151519", fg: "#f4f4f6", muted: "#a2a2ab", line: "#26262c", brand: "#a998f8", brandWeak: "#1d1730" },
};

const PLATFORM_NAMES: Record<string, string> = {
  YOUTUBE: "YouTube",
  INSTAGRAM: "Instagram",
  TIKTOK: "TikTok",
};

const compact = new Intl.NumberFormat("tr-TR", { notation: "compact", maximumFractionDigits: 1 });

export default async function OgImage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  let kit = null;
  try {
    kit = await getKit(slug);
  } catch {
    // Backend unreachable and nothing cached: fall through to the generic card.
  }

  const t = THEME[kit?.theme === "dark" ? "dark" : "light"];

  // Missing kit (or fetch failure): a neutral brand card, nothing leaked.
  if (!kit) {
    return new ImageResponse(
      (
        <div style={{ width: "100%", height: "100%", display: "flex", alignItems: "center", justifyContent: "center", background: THEME.light.page, color: THEME.light.fg, fontSize: 64, fontWeight: 700 }}>
          LocalMediaKit
        </div>
      ),
      size
    );
  }

  // Protected kit: the gate only — no stats, no headline (nothing sensitive
  // reaches the edge, and social cards are the most public surface there is).
  if (kit.isProtected) {
    return new ImageResponse(
      (
        <div style={{ width: "100%", height: "100%", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", background: t.page, color: t.fg, gap: 24 }}>
          {/* A padlock drawn with plain boxes (satori has no icon fonts). */}
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
            <div style={{ display: "flex", width: 44, height: 26, borderTopLeftRadius: 22, borderTopRightRadius: 22, borderLeft: `7px solid ${t.brand}`, borderRight: `7px solid ${t.brand}`, borderTop: `7px solid ${t.brand}` }} />
            <div style={{ display: "flex", width: 76, height: 54, borderRadius: 14, background: t.brand, marginTop: -2 }} />
          </div>
          <div style={{ display: "flex", fontSize: 56, fontWeight: 700 }}>{kit.title}</div>
          <div style={{ display: "flex", fontSize: 30, color: t.muted }}>Sifre korumali medya kiti</div>
        </div>
      ),
      size
    );
  }

  const initial = (kit.displayName || kit.title).charAt(0).toUpperCase();
  const platforms = (kit.platforms ?? []).slice(0, 3);

  return new ImageResponse(
    (
      <div style={{ width: "100%", height: "100%", display: "flex", flexDirection: "column", justifyContent: "space-between", background: t.page, color: t.fg, padding: 64 }}>
        {/* Hero: initial + name/title/headline */}
        <div style={{ display: "flex", alignItems: "center", gap: 40 }}>
          <div style={{ display: "flex", width: 160, height: 160, borderRadius: 999, background: t.brandWeak, color: t.brand, alignItems: "center", justifyContent: "center", fontSize: 72, fontWeight: 700, border: `2px solid ${t.line}` }}>
            {initial}
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 10, maxWidth: 860 }}>
            <div style={{ display: "flex", fontSize: 26, letterSpacing: 4, textTransform: "uppercase", color: t.muted }}>
              {kit.displayName}
            </div>
            <div style={{ display: "flex", fontSize: 64, fontWeight: 700, lineHeight: 1.1 }}>{kit.title}</div>
            {kit.headline && (
              <div style={{ display: "flex", fontSize: 30, color: t.muted, lineHeight: 1.3 }}>
                {kit.headline.length > 90 ? kit.headline.slice(0, 90) + "…" : kit.headline}
              </div>
            )}
          </div>
        </div>

        {/* Platform pills */}
        <div style={{ display: "flex", gap: 20 }}>
          {platforms.map((p) => (
            <div key={p.platform} style={{ display: "flex", flexDirection: "column", gap: 6, background: t.surface, border: `2px solid ${t.line}`, borderRadius: 24, padding: "24px 32px" }}>
              <div style={{ display: "flex", fontSize: 24, color: t.muted }}>
                {PLATFORM_NAMES[p.platform] ?? p.platform}
              </div>
              <div style={{ display: "flex", alignItems: "baseline", gap: 10 }}>
                <div style={{ display: "flex", fontSize: 44, fontWeight: 700 }}>{compact.format(p.followers)}</div>
                <div style={{ display: "flex", fontSize: 24, color: t.muted }}>takipci</div>
              </div>
            </div>
          ))}
        </div>

        {/* Footer: wordmark only when the frozen badge rule says so */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", borderTop: `2px solid ${t.line}`, paddingTop: 28 }}>
          <div style={{ display: "flex", fontSize: 26, color: t.muted }}>/{kit.slug}</div>
          {kit.showBadge !== false && (
            <div style={{ display: "flex", fontSize: 26, fontWeight: 600, color: t.brand }}>LocalMediaKit</div>
          )}
        </div>
      </div>
    ),
    size
  );
}
