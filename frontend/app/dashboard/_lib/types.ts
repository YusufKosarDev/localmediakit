/** Shapes the dashboard reads from the API, shared by the shell and its panels. */

export type Me = {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  theme: string;
  leadNotificationsEnabled: boolean;
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

export type Tab = "edit" | "stats" | "collabs" | "leads" | "analytics" | "versions" | "domain";

export const TABS: { id: Tab; label: string }[] = [
  { id: "edit", label: "Duzenle" },
  { id: "stats", label: "Istatistik & Kitle" },
  { id: "collabs", label: "Isbirlikleri & Ucretler" },
  { id: "leads", label: "Gelen Kutusu" },
  { id: "analytics", label: "Analitik" },
  { id: "versions", label: "Versiyonlar" },
  { id: "domain", label: "Domain" },
];

/**
 * The curated looks a public page can have. Mirrors KitAppearance on the
 * backend, which rejects anything outside these lists.
 *
 * <p>A fixed set rather than free colour input: every accent's contrast is
 * verified against the surfaces it renders on (tests/palette.test.ts), so a
 * user cannot produce an inaccessible page.
 */
export const ACCENTS: { id: string; label: string; swatch: string }[] = [
  { id: "violet", label: "Menekse", swatch: "#6d40e6" },
  { id: "ocean", label: "Okyanus", swatch: "#407796" },
  { id: "forest", label: "Orman", swatch: "#367d5c" },
  { id: "amber", label: "Kehribar", swatch: "#98653a" },
  { id: "rose", label: "Gul", swatch: "#c14469" },
  { id: "graphite", label: "Grafit", swatch: "#5f7195" },
];

export const LAYOUTS: { id: string; label: string; hint: string }[] = [
  { id: "classic", label: "Klasik", hint: "Ortalanmis, dar kolon" },
  { id: "panel", label: "Panel", hint: "Sola hizali, genis kolon" },
];

/** Presentation languages a kit can be published in. */
export const LANGUAGES: { id: string; label: string }[] = [
  { id: "tr", label: "Turkce" },
  { id: "en", label: "English" },
];

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
