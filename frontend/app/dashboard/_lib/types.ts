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
