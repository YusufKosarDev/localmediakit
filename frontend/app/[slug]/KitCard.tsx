import { Play, Camera, Music, ArrowUpRight, ArrowDownRight, Globe } from "lucide-react";
import TrackView from "./TrackView";
import PrintButton from "./PrintButton";
import ContactForm from "./ContactForm";

// Presentational, framework-neutral: rendered by the server page for public
// kits AND by the client PasswordGate after a protected kit is unlocked, so the
// two paths share one design with no duplication. No hooks here — stays static.

export type PlatformStat = {
  platform: string;
  followers: number;
  avgViews: number | null;
  avgLikes: number | null;
  avgComments: number | null;
  engagementRate: number | null;
  followerGrowth30d: number | null;
};

export type Demographic = { category: string; label: string; percentage: number };

export type Collaboration = {
  brandName: string;
  campaign: string | null;
  period: string | null;
  resultNote: string | null;
  logoUrl: string | null;
};

export type RateCardItem = {
  serviceName: string;
  priceAmount: number;
  currency: string;
  note: string | null;
};

export type PublicKit = {
  slug: string;
  title: string;
  headline: string | null;
  avatarUrl: string | null;
  theme: string;
  displayName: string;
  platforms: PlatformStat[];
  demographics: Demographic[];
  collaborations: Collaboration[];
  rateCard: RateCardItem[] | null;
  showBadge: boolean;
  contactEnabled: boolean;
  isProtected: boolean;
  version: number;
  publishedAt: string;
};

const PLATFORMS: Record<
  string,
  { name: string; Icon: typeof Play; className: string }
> = {
  YOUTUBE: { name: "YouTube", Icon: Play, className: "bg-red-500/10 text-red-600 dark:text-red-400" },
  INSTAGRAM: { name: "Instagram", Icon: Camera, className: "bg-pink-500/10 text-pink-600 dark:text-pink-400" },
  TIKTOK: { name: "TikTok", Icon: Music, className: "bg-teal-500/10 text-teal-600 dark:text-teal-400" },
};

const CATEGORY_NAMES: Record<string, string> = { AGE: "Yas", GENDER: "Cinsiyet", COUNTRY: "Ulke" };

const compact = new Intl.NumberFormat("tr-TR", { notation: "compact", maximumFractionDigits: 1 });

function fmtPct(n: number): string {
  return n.toLocaleString("tr-TR", { maximumFractionDigits: 2 });
}

function fmtPrice(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat("tr-TR", {
      style: "currency",
      currency,
      maximumFractionDigits: 0,
    }).format(amount);
  } catch {
    return `${amount.toLocaleString("tr-TR")} ${currency}`;
  }
}

// preview: renders the same card for a live DRAFT (owner's short-lived link).
// No analytics beacon (previews must not pollute visitor stats) and the footer
// says so instead of showing a publish date.
export default function KitCard({ kit, preview = false }: { kit: PublicKit; preview?: boolean }) {
  const dark = kit.theme === "dark";
  const publishedDate = new Date(kit.publishedAt).toLocaleDateString("tr-TR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
  const demographicsByCategory = ["AGE", "GENDER", "COUNTRY"]
    .map((category) => ({ category, entries: kit.demographics.filter((d) => d.category === category) }))
    .filter((g) => g.entries.length > 0);
  // Older snapshots predate the rate card; normalize the absent list.
  const rateCard = kit.rateCard ?? [];

  return (
    <div data-theme={dark ? "dark" : "light"} className={dark ? "dark" : ""}>
      <main className="kit-root min-h-screen bg-page text-fg">
        {!preview && <TrackView slug={kit.slug} />}
        {preview && (
          <div className="no-print sticky top-0 z-10 border-b border-line bg-brand-weak px-4 py-2 text-center text-xs font-medium text-brand">
            ONIZLEME — bu sayfa yayinlanmamis taslagi gosterir; link kisa sureli ve gecicidir.
          </div>
        )}
        <div className="mx-auto w-full max-w-2xl px-5 py-12 sm:py-16">
          <PrintButton />

          {/* Hero */}
          <header className="animate-rise flex flex-col items-center text-center">
            {kit.avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={kit.avatarUrl}
                alt={kit.displayName}
                width={104}
                height={104}
                loading="lazy"
                decoding="async"
                className="h-26 w-26 rounded-full object-cover ring-1 ring-line"
                style={{ height: 104, width: 104 }}
              />
            ) : (
              <div
                aria-hidden
                className="grid place-items-center rounded-full bg-brand-weak text-3xl font-semibold text-brand ring-1 ring-line"
                style={{ height: 104, width: 104 }}
              >
                {kit.displayName.charAt(0).toUpperCase()}
              </div>
            )}
            <p className="mt-5 text-xs font-medium uppercase tracking-[0.18em] text-muted">
              {kit.displayName}
            </p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">{kit.title}</h1>
            {kit.headline && (
              <p className="mt-3 max-w-md text-[15px] leading-relaxed text-muted">{kit.headline}</p>
            )}
          </header>

          {/* Platforms */}
          {kit.platforms.length > 0 && (
            <Section title="Platformlar" delay="0.06s">
              <div className="grid gap-3 sm:grid-cols-2">
                {kit.platforms.map((p) => {
                  const meta = PLATFORMS[p.platform] ?? {
                    name: p.platform,
                    Icon: Globe,
                    className: "bg-brand-weak text-brand",
                  };
                  return (
                    <div
                      key={p.platform}
                      className="rounded-2xl border border-line bg-surface p-4 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className={`grid h-8 w-8 place-items-center rounded-lg ${meta.className}`}>
                            <meta.Icon className="h-4 w-4" />
                          </span>
                          <span className="text-sm font-medium">{meta.name}</span>
                        </div>
                        {p.followerGrowth30d != null && <GrowthBadge value={p.followerGrowth30d} />}
                      </div>
                      <div className="mt-3 flex items-baseline gap-1.5">
                        <span className="text-2xl font-semibold tabular-nums tracking-tight">
                          {compact.format(p.followers)}
                        </span>
                        <span className="text-xs text-muted">takipci</span>
                      </div>
                      {p.engagementRate != null && (
                        <div className="mt-1 text-sm text-muted">
                          <span className="font-medium text-fg tabular-nums">%{fmtPct(p.engagementRate)}</span>{" "}
                          etkilesim
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </Section>
          )}

          {/* Demographics */}
          {demographicsByCategory.length > 0 && (
            <Section title="Kitle" delay="0.12s">
              <div className="grid gap-6 rounded-2xl border border-line bg-surface p-5 shadow-[0_1px_2px_rgba(0,0,0,0.04)] sm:grid-cols-3">
                {demographicsByCategory.map((group) => (
                  <div key={group.category}>
                    <div className="mb-3 text-xs font-medium uppercase tracking-wider text-faint">
                      {CATEGORY_NAMES[group.category] ?? group.category}
                    </div>
                    <div className="grid gap-2.5">
                      {group.entries.map((d) => (
                        <div key={d.label}>
                          <div className="mb-1 flex items-center justify-between text-[13px]">
                            <span>{d.label}</span>
                            <span className="tabular-nums text-muted">%{fmtPct(d.percentage)}</span>
                          </div>
                          <div className="h-1.5 overflow-hidden rounded-full bg-brand-weak">
                            <div
                              className="h-full rounded-full bg-brand-strong"
                              style={{ width: `${Math.min(d.percentage, 100)}%` }}
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </Section>
          )}

          {/* Collaborations */}
          {kit.collaborations.length > 0 && (
            <Section title="Marka Isbirlikleri" delay="0.18s">
              <div className="grid gap-2.5">
                {kit.collaborations.map((col, i) => (
                  <div
                    key={i}
                    className="flex items-center gap-3.5 rounded-2xl border border-line bg-surface p-4 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
                  >
                    {col.logoUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={col.logoUrl}
                        alt={col.brandName}
                        loading="lazy"
                        decoding="async"
                        className="h-10 w-10 shrink-0 rounded-xl object-cover ring-1 ring-line"
                        style={{ height: 40, width: 40 }}
                      />
                    ) : (
                      <div
                        aria-hidden
                        className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-weak font-semibold text-brand"
                      >
                        {col.brandName.charAt(0).toUpperCase()}
                      </div>
                    )}
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-baseline gap-x-2">
                        <span className="font-medium">{col.brandName}</span>
                        {col.period && <span className="text-xs text-faint">{col.period}</span>}
                      </div>
                      {col.campaign && <div className="text-sm">{col.campaign}</div>}
                      {col.resultNote && <div className="text-[13px] text-muted">{col.resultNote}</div>}
                    </div>
                  </div>
                ))}
              </div>
            </Section>
          )}

          {/* Rate card */}
          {rateCard.length > 0 && (
            <Section title="Calisma Ucretleri" delay="0.24s">
              <div className="overflow-hidden rounded-2xl border border-line bg-surface shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
                {rateCard.map((r, i) => (
                  <div
                    key={i}
                    className={`flex items-baseline justify-between gap-4 px-4 py-3 ${i > 0 ? "border-t border-line" : ""}`}
                  >
                    <div className="min-w-0">
                      <div className="font-medium">{r.serviceName}</div>
                      {r.note && <div className="text-[13px] text-muted">{r.note}</div>}
                    </div>
                    <div className="shrink-0 font-semibold tabular-nums">{fmtPrice(r.priceAmount, r.currency)}</div>
                  </div>
                ))}
              </div>
            </Section>
          )}

          {/* Contact form (frozen flag; previews never show it) */}
          {kit.contactEnabled && !preview && (
            <Section title="Iletisim" delay="0.3s">
              <ContactForm slug={kit.slug} />
            </Section>
          )}

          <footer className="mt-10 border-t border-line pt-5 text-center text-xs text-faint">
            {preview ? "Onizleme — henuz yayinlanmadi" : `${publishedDate} tarihinde yayinlandi`}
            {kit.showBadge !== false && (
              <>
                {" · "}
                <span className="font-medium text-muted">LocalMediaKit</span>
              </>
            )}
          </footer>
        </div>
      </main>
    </div>
  );
}

function Section({ title, delay, children }: { title: string; delay: string; children: React.ReactNode }) {
  return (
    <section className="animate-rise mt-9" style={{ animationDelay: delay }}>
      <h2 className="mb-3 text-xs font-medium uppercase tracking-[0.14em] text-faint">{title}</h2>
      {children}
    </section>
  );
}

function GrowthBadge({ value }: { value: number }) {
  const up = value >= 0;
  return (
    <span
      className={`inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-xs font-medium tabular-nums ${
        up ? "bg-success/10 text-success" : "bg-danger/10 text-danger"
      }`}
    >
      {up ? <ArrowUpRight className="h-3 w-3" /> : <ArrowDownRight className="h-3 w-3" />}
      {up ? "+" : ""}
      {fmtPct(value)}%
      <span className="text-[10px] font-normal">30g</span>
    </span>
  );
}
