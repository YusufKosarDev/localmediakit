import type { PublicKit } from "@/app/[slug]/KitCard";

// A full, current-schema snapshot. Individual tests clone and tweak it so each
// case states exactly the field it exercises.
export function makeKit(overrides: Partial<PublicKit> = {}): PublicKit {
  return {
    slug: "gezgin-ayse",
    title: "Ayse Gezgin",
    headline: "Seyahat ve yasam tarzi",
    avatarUrl: null,
    theme: "light",
    displayName: "Ayse",
    platforms: [
      {
        platform: "INSTAGRAM",
        followers: 64000,
        avgViews: null,
        avgLikes: 3800,
        avgComments: 340,
        engagementRate: 6.47,
        followerGrowth30d: 12.5,
      },
    ],
    demographics: [
      { category: "AGE", label: "18-24", percentage: 42 },
      { category: "GENDER", label: "Kadin", percentage: 61 },
    ],
    collaborations: [
      { brandName: "Kahve Dunyasi", campaign: "Bahar kampanyasi", period: "2025", resultNote: null, logoUrl: null },
    ],
    rateCard: [
      { serviceName: "Instagram Reels", priceAmount: 15000, currency: "TRY", note: null },
    ],
    showBadge: true,
    contactEnabled: true,
    isProtected: false,
    version: 3,
    publishedAt: "2026-05-10T12:00:00Z",
    ...overrides,
  };
}
