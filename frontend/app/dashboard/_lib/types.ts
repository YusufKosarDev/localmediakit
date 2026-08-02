/** Shapes the dashboard reads from the API, shared by the shell and its panels. */
import type { DashboardStrings } from "@/app/_i18n/dashboard";

export type Me = {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  theme: string;
  leadNotificationsEnabled: boolean;
  locale: string;
};

export type Kit = {
  id: number;
  slug: string;
  title: string;
  headline: string | null;
  avatarUrl: string | null;
  theme: string;
  accent: string;
  layout: string;
  language: string;
  status: string;
  publishedSlug: string | null;
  passwordProtected: boolean;
  contactEnabled: boolean;
};

export type Version = { version: number; slug: string; publishedAt: string; active: boolean };

export type Stat = {
  platform: string;
  followers: number;
  avgViews: number | null;
  avgLikes: number | null;
  avgComments: number | null;
  engagementRate: number | null;
  followerGrowth30d: number | null;
};

export type DemoEntry = { category: string; label: string; percentage: number | string };

/**
 * A labelled link the creator sends to one brand. `url` is relative -- the
 * backend assembles the shape, the dashboard prefixes its own origin, so
 * neither side has to know the other's host.
 */
export type ShareLink = {
  id: number;
  label: string;
  token: string;
  url: string;
  active: boolean;
  views: number;
  uniqueVisitors: number;
  createdAt: string;
  revokedAt: string | null;
};

export type MediaItem = {
  id: number;
  title: string;
  url: string;
  thumbnailUrl: string | null;
  platform: string | null;
  note: string | null;
  displayOrder: number;
};

export type Domain = {
  id: number;
  domain: string;
  status: string;
  attempts: number;
  lastCheckedAt: string | null;
  dnsRecordType: string;
  dnsRecordHost: string;
  dnsRecordValue: string;
};

export type Analytics = {
  totalViews: number;
  uniqueVisitors: number | null;
  viewsByDay: { date: string; views: number; uniqueVisitors: number }[] | null;
  referrers: { label: string; count: number }[] | null;
  devices: { label: string; count: number }[] | null;
};

export type Collab = {
  id: number;
  brandName: string;
  campaign: string | null;
  period: string | null;
  resultNote: string | null;
  logoUrl: string | null;
  displayOrder: number;
};

export type RateItem = {
  id: number;
  serviceName: string;
  priceAmount: number | string;
  currency: string;
  note: string | null;
  displayOrder: number;
};

export type Lead = {
  id: number;
  brandName: string;
  email: string;
  message: string;
  status: string;
  createdAt: string;
};

export type SyncSource = {
  platform: string;
  externalId: string;
  lastSyncedAt: string | null;
  lastError: string | null;
};

export type SyncStatus = {
  availablePlatforms: string[];
  autoSync: boolean;
  sources: SyncSource[];
};

export type MetricChange = { metric: string; from: string | null; to: string | null };

export type VersionDiff = {
  fromVersion: number;
  toVersion: number;
  fields: { field: string; from: string | null; to: string | null }[];
  platforms: { platform: string; kind: string; changes: MetricChange[] }[];
  collaborations: { added: string[]; removed: string[]; changed: MetricChange[] };
  rateCard: { added: string[]; removed: string[]; changed: MetricChange[] };
  demographics: { added: string[]; removed: string[]; changed: MetricChange[] };
};

export type Tab =
  | "edit" | "stats" | "media" | "collabs" | "leads" | "analytics" | "versions" | "domain";





export const PLATFORMS = ["YOUTUBE", "INSTAGRAM", "TIKTOK"];
export const CATEGORIES = ["AGE", "GENDER", "COUNTRY"];

/**
 * Reported by every panel to the shell, which owns the single notice/error
 * card at the top of the page. Passing these two callbacks keeps that one
 * place authoritative without a context or a store.
 */
export type Feedback = {
  notify: (message: string) => void;
  fail: (message: string) => void;
  clear: () => void;
};

/** Bound translator for the dashboard dictionary, threaded down to panels. */
export type Translate = (
  key: keyof DashboardStrings,
  vars?: Record<string, string | number>
) => string;

/**
 * Tab, accent, layout and language options.
 *
 * <p>Functions of the translator rather than constants: their labels are user
 * copy, and a module-level constant would freeze them in one language at
 * import time.
 */
export const tabs = (t: Translate): { id: Tab; label: string }[] => [
  { id: "edit", label: t("tabEdit") },
  { id: "stats", label: t("tabStats") },
  // Next to the other content lists rather than at the end: the work and the
  // brands who bought it are the same kind of thing to a creator filling this in.
  { id: "media", label: t("tabMedia") },
  { id: "collabs", label: t("tabCollabs") },
  { id: "leads", label: t("tabLeads") },
  { id: "analytics", label: t("tabAnalytics") },
  { id: "versions", label: t("tabVersions") },
  { id: "domain", label: t("tabDomain") },
];

/**
 * Every accent's contrast is verified against the surfaces it renders on
 * (tests/palette.test.ts), so a user cannot produce an inaccessible page.
 */
export const accents = (t: Translate): { id: string; label: string; swatch: string }[] => [
  { id: "violet", label: t("accentViolet"), swatch: "#6d40e6" },
  { id: "ocean", label: t("accentOcean"), swatch: "#407796" },
  { id: "forest", label: t("accentForest"), swatch: "#367d5c" },
  { id: "amber", label: t("accentAmber"), swatch: "#98653a" },
  { id: "rose", label: t("accentRose"), swatch: "#c14469" },
  { id: "graphite", label: t("accentGraphite"), swatch: "#5f7195" },
];

export const layouts = (t: Translate): { id: string; label: string; hint: string }[] => [
  { id: "classic", label: t("layoutClassic"), hint: t("layoutClassicHint") },
  { id: "panel", label: t("layoutPanel"), hint: t("layoutPanelHint") },
];

/** Presentation languages a kit can be published in — endonyms, not translated. */
export const LANGUAGES: { id: string; label: string }[] = [
  { id: "tr", label: "Turkce" },
  { id: "en", label: "English" },
];
