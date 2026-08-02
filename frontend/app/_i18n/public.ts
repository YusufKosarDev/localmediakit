import type { Dict } from "./index";

/**
 * Strings for the published media-kit page.
 *
 * <p>Kept in its own module, separate from the dashboard's, because this is
 * the surface with the tightest First Load budget in the project. Only these
 * strings ship to a visitor reading a kit — the dashboard's several hundred
 * never reach them.
 */
const publicStrings = {
  sectionPlatforms: "Platformlar",
  sectionAudience: "Kitle",
  sectionCollaborations: "Marka Isbirlikleri",
  sectionMedia: "Icerikler",
  sectionRateCard: "Calisma Ucretleri",
  sectionContact: "Iletisim",

  followers: "takipci",
  engagement: "etkilesim",
  growth30d: "30g",

  categoryAge: "Yas",
  categoryGender: "Cinsiyet",
  categoryCountry: "Ulke",

  previewBanner: "ONIZLEME — bu sayfa yayinlanmamis taslagi gosterir; link kisa sureli ve gecicidir.",
  previewFooter: "Onizleme — henuz yayinlanmadi",
  publishedOn: "{date} tarihinde yayinlandi",

  contactIntro: "Bu uretici ile calismak ister misiniz? Teklifinizi iletin.",
  contactBrand: "Marka / sirket adi *",
  contactEmail: "E-posta *",
  contactMessage: "Mesajiniz *",
  contactSubmit: "Teklif gonder",
  contactSending: "Gonderiliyor...",
  contactSent: "Teklifiniz iletildi.",
  contactSentHint: "Uretici en kisa surede sizinle iletisime gececek.",
  contactRateLimited: "Cok fazla istek. Lutfen birkac dakika sonra tekrar deneyin.",
  contactFailed: "Baglanti hatasi. Tekrar deneyin.",

  lockedTitle: "Bu medya kiti sifre korumali.",
  lockedHint: "Goruntulemek icin sifreyi girin.",
  lockedPassword: "Sifre",
  lockedSubmit: "Goruntule",
  lockedWrong: "Sifre yanlis.",
  lockedTooMany: "Cok fazla deneme. Biraz bekleyin.",

  busy: "...",
  checking: "Kontrol ediliyor...",
  printButton: "PDF olarak kaydet",
} as const;

export type PublicStrings = typeof publicStrings;

export const publicDict: Dict<Record<keyof PublicStrings, string>> = {
  tr: publicStrings,
  en: {
    sectionPlatforms: "Platforms",
    sectionAudience: "Audience",
    sectionCollaborations: "Brand Collaborations",
    sectionMedia: "Work",
    sectionRateCard: "Rates",
    sectionContact: "Contact",

    followers: "followers",
    engagement: "engagement",
    growth30d: "30d",

    categoryAge: "Age",
    categoryGender: "Gender",
    categoryCountry: "Country",

    previewBanner: "PREVIEW — this page shows an unpublished draft; the link is temporary and short-lived.",
    previewFooter: "Preview — not published yet",
    publishedOn: "Published on {date}",

    contactIntro: "Interested in working with this creator? Send your enquiry.",
    contactBrand: "Brand / company name *",
    contactEmail: "Email *",
    contactMessage: "Your message *",
    contactSubmit: "Send enquiry",
    contactSending: "Sending...",
    contactSent: "Your enquiry has been sent.",
    contactSentHint: "The creator will get back to you shortly.",
    contactRateLimited: "Too many requests. Please try again in a few minutes.",
    contactFailed: "Connection error. Please try again.",

    lockedTitle: "This media kit is password-protected.",
    lockedHint: "Enter the password to view it.",
    lockedPassword: "Password",
    lockedSubmit: "View",
    lockedWrong: "Incorrect password.",
    lockedTooMany: "Too many attempts. Please wait a moment.",

    busy: "...",
    checking: "Checking...",
    printButton: "Save as PDF",
  },
};
