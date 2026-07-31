import { Play, Camera, Music, ArrowUpRight, ArrowDownRight, Globe } from "lucide-react";
import TrackView from "./TrackView";
import PrintButton from "./PrintButton";
import ContactForm from "./ContactForm";
import {
  normalizeLocale, translator, formatCompact, formatDate, formatNumber, intlTag, type Locale,
} from "@/app/_i18n";
import { publicDict } from "@/app/_i18n/public";

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
  /** Curated accent. Absent on snapshots published before accents existed. */
  accent?: string | null;
  /** Curated layout variant. Absent on older snapshots. */
  layout?: string | null;
  /** Presentation language, frozen at publish. Absent on older snapshots. */
  language?: string | null;
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



/** Accents this build knows how to style; anything else falls back to violet. */
const ACCENTS = ["violet", "ocean", "forest", "amber", "rose", "graphite"];



/**
 * Percentages differ in more than the decimal separator: Turkish writes the
 * sign first (%6,47), English last (6.47%). Formatting the number and pasting
 * a "%" in the JSX would get one of the two wrong.
 */
function fmtPct(n: number, locale: Locale): string {
  const value = n.toLocaleString(intlTag(locale), { maximumFractionDigits: 2 });
  return locale === "tr" ? `%${value}` : `${value}%`;
}

function fmtPrice(amount: number, currency: string, locale: Locale): string {
  try {
    return new Intl.NumberFormat(intlTag(locale), {
      style: "currency",
      currency,
      maximumFractionDigits: 0,
    }).format(amount);
  } catch {
    return `${amount.toLocaleString(intlTag(locale))} ${currency}`;
  }
}

// preview: renders the same card for a live DRAFT (owner's short-lived link).
// No analytics beacon (previews must not pollute visitor stats) and the footer
// says so instead of showing a publish date.
export default function KitCard({ kit, preview = false }: { kit: PublicKit; preview?: boolean }) {
  const dark = kit.theme === "dark";
  // Rendering stays forgiving even though the API validates on write: a
  // snapshot from before these existed (or any value this build does not know)
  // renders as the original look rather than as an unstyled page.
  // Language comes from the published snapshot, not from the visitor: one URL
  // renders in one language and stays a single edge-cache entry.
  const locale = normalizeLocale(kit.language);
  const t = translator(publicDict, locale);
  const compact = (n: number) => formatCompact(n, locale);
  const CATEGORY_NAMES: Record<string, string> = {
    AGE: t("categoryAge"), GENDER: t("categoryGender"), COUNTRY: t("categoryCountry"),
  };
  const accent = ACCENTS.includes(kit.accent ?? "") ? kit.accent! : "violet";
  const panel = kit.layout === "panel";
  const publishedDate = formatDate(kit.publishedAt, locale);
  const demographicsByCategory = ["AGE", "GENDER", "COUNTRY"]
    .map((category) => ({ category, entries: kit.demographics.filter((d) => d.category === category) }))
    .filter((g) => g.entries.length > 0);
  // Older snapshots predate the rate card; normalize the absent list.
  const rateCard = kit.rateCard ?? [];

  return (
    // lang sits on the kit's own wrapper rather than <html>: App Router
    // renders <html> only in the shared root layout, and making that per-route
    // would need either multiple root layouts or headers(), which would cost
    // this page its static generation. A lang on an ancestor element is valid
    // HTML and is what assistive tech and crawlers read for this subtree.
    <div
      lang={locale}
      data-theme={dark ? "dark" : "light"}
      data-accent={accent}
      className={dark ? "dark" : ""}
    >
      <main className="kit-root min-h-screen bg-page text-fg">
        {!preview && <TrackView slug={kit.slug} />}
        {preview && (
          <div className="no-print sticky top-0 z-10 border-b border-line bg-brand-weak px-4 py-2 text-center text-xs font-medium text-brand">
            {t("previewBanner")}
          </div>
        )}
        {/* The layout variants swap Tailwind classes on the SAME markup — no
            element is added, removed or reordered — so heading order, landmarks
            and reading order are identical in both. */}
        <div className={`mx-auto w-full px-5 py-12 sm:py-16 ${panel ? "max-w-3xl" : "max-w-2xl"}`}>
          <PrintButton />

          {/* Hero */}
          <header
            className={`animate-rise flex flex-col ${
              panel ? "items-start text-left" : "items-center text-center"
            }`}
          >
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
            <Section title={t("sectionPlatforms")} delay="0.06s">
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
                        {p.followerGrowth30d != null && <GrowthBadge value={p.followerGrowth30d} locale={locale} growthLabel={t("growth30d")} />}
                      </div>
                      <div className="mt-3 flex items-baseline gap-1.5">
                        <span className="text-2xl font-semibold tabular-nums tracking-tight">
                          {compact(p.followers)}
                        </span>
                        <span className="text-xs text-muted">{t("followers")}</span>
                      </div>
                      {p.engagementRate != null && (
                        <div className="mt-1 text-sm text-muted">
                          <span className="font-medium text-fg tabular-nums">{fmtPct(p.engagementRate, locale)}</span>{" "}
                          {t("engagement")}
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
            <Section title={t("sectionAudience")} delay="0.12s">
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
                            <span className="tabular-nums text-muted">{fmtPct(d.percentage, locale)}</span>
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
            <Section title={t("sectionCollaborations")} delay="0.18s">
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
            <Section title={t("sectionRateCard")} delay="0.24s">
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
                    <div className="shrink-0 font-semibold tabular-nums">{fmtPrice(r.priceAmount, r.currency, locale)}</div>
                  </div>
                ))}
              </div>
            </Section>
          )}

          {/* Contact form (frozen flag; previews never show it) */}
          {kit.contactEnabled && !preview && (
            <Section title={t("sectionContact")} delay="0.3s">
              <ContactForm slug={kit.slug} locale={locale} />
            </Section>
          )}

          <footer className="mt-10 border-t border-line pt-5 text-center text-xs text-faint">
            {preview ? t("previewFooter") : t("publishedOn", { date: publishedDate })}
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

function GrowthBadge({ value, locale, growthLabel }: { value: number; locale: Locale; growthLabel: string }) {
  const up = value >= 0;
  return (
    <span
      className={`inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-xs font-medium tabular-nums ${
        up ? "bg-success/10 text-success" : "bg-danger/10 text-danger"
      }`}
    >
      {up ? <ArrowUpRight className="h-3 w-3" /> : <ArrowDownRight className="h-3 w-3" />}
      {up ? "+" : ""}
      {/* A signed delta, so the sign trails the number in both languages
          ("+12,5%"). Deliberately not fmtPct, which puts "%" first in Turkish:
          that is the convention for a plain rate (%6,47 etkilesim) and this
          badge has always rendered the other way. Changing it here would be a
          visible edit to the public page under cover of a translation step. */}
      {formatNumber(value, locale)}%
      <span className="text-[10px] font-normal">{growthLabel}</span>
    </span>
  );
}
