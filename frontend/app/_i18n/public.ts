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
  sectionCollaborations: "Marka İş Birlikleri",
  sectionMedia: "İçerikler",
  sectionRateCard: "Çalışma Ücretleri",
  sectionContact: "İletişim",

  followers: "takipçi",
  engagement: "etkileşim",
  growth30d: "30g",

  categoryAge: "Yaş",
  categoryGender: "Cinsiyet",
  categoryCountry: "Ülke",

  previewBanner: "ÖNİZLEME — bu sayfa yayınlanmamış taslağı gösterir; link kısa süreli ve geçicidir.",
  previewFooter: "Önizleme — henüz yayınlanmadı",
  publishedOn: "{date} tarihinde yayınlandı",

  contactIntro: "Bu üretici ile çalışmak ister misiniz? Teklifinizi iletin.",
  contactBrand: "Marka / şirket adı *",
  contactEmail: "E-posta *",
  contactMessage: "Mesajınız *",
  contactSubmit: "Teklif gönder",
  contactSending: "Gönderiliyor...",
  contactSent: "Teklifiniz iletildi.",
  contactSentHint: "Üretici en kısa sürede sizinle iletişime geçecek.",
  contactRateLimited: "Çok fazla istek. Lütfen birkaç dakika sonra tekrar deneyin.",
  contactFailed: "Bağlantı hatası. Tekrar deneyin.",

  lockedTitle: "Bu medya kiti şifre korumalı.",
  lockedHint: "Görüntülemek için şifreyi girin.",
  lockedPassword: "Şifre",
  lockedSubmit: "Görüntüle",
  lockedWrong: "Şifre yanlış.",
  lockedTooMany: "Çok fazla deneme. Biraz bekleyin.",

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
