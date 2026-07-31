import type { Dict } from "./index";

/**
 * Landing, auth and the standalone app surfaces.
 *
 * <p>Separate from the dashboard's dictionary so a visitor who only ever sees
 * the marketing page does not download several hundred dashboard strings, and
 * separate from the public kit page's for the same reason.
 */
const appStrings = {
  navSignIn: "Giris",
  navSignUp: "Kayit ol",
  langLabel: "Dil",

  heroBadge: "Canli · edge'den servis edilir",
  heroTitleBefore: "Medya kitiniz, markaya ",
  heroTitleAccent: "hazir",
  heroTitleAfter: " bir link.",
  heroBody:
    "Istatistik, etkilesim orani, demografi ve marka isbirliklerini tek, sik bir sayfada toplayin ve markalarla paylasin.",
  heroSeeDemo: "Ornek medya kitini gor",
  heroBrowseDemo: "Demo olarak gez",

  featureStatsTitle: "Istatistik & etkilesim",
  featureStatsBody:
    "Platform basina takipci, ortalama izlenme ve platforma ozel etkilesim orani — trend rozetleriyle.",
  featureAudienceTitle: "Kitle demografisi",
  featureAudienceBody: "Yas, cinsiyet ve ulke dagilimi; markaya kime ulastigini net gosterir.",
  featureCollabsTitle: "Marka isbirlikleri",
  featureCollabsBody: "Gecmis kampanyalar ve sonuclariyla sosyal kanit vitrini.",

  footer: "Ucretsiz medya kiti araci — tum ozellikler herkese acik.",

  loginTitle: "Giris yap",
  loginSubtitle: "Panonuza erisin.",
  loginEmail: "Email",
  loginPassword: "Sifre",
  loginSubmit: "Giris yap",
  loginOr: "veya",
  loginDemo: "Demo olarak gez",
  loginDemoHint: "Dolu bir hesapla panoyu kesfedin (gece sifirlanir).",
  loginNoAccount: "Hesabin yok mu?",
  loginSignUp: "Kayit ol",
  loginFailed: "Giris basarisiz (email/sifre hatali).",
  loginThrottled: "Cok fazla deneme. Lutfen biraz bekleyin.",
  loginUnreachable: "Sunucuya ulasilamadi.",

  registerTitle: "Hesap olustur",
  registerSubtitle: "Ilk medya kitinizi dakikalar icinde yayinlayin.",
  registerName: "Gorunen ad",
  registerSubmit: "Kayit ol",
  registerHaveAccount: "Zaten hesabin var mi?",
  registerSignIn: "Giris yap",
  registerPasswordHint: "En az 8 karakter",
  registerEmailTaken: "Bu e-posta zaten kayitli.",
  registerInvalid: "Bilgileri kontrol edin (sifre en az 8 karakter).",
  registerFailed: "Kayit olusturulamadi.",

  offlineTitle: "Baglanti yok",
  offlineBody:
    "Panonuzdaki her sey sunucudan canli okunur, bu yuzden cevrimdisi gosterilebilecek bir icerik yok. Baglantiniz gelince kaldiginiz yerden devam edebilirsiniz.",
  offlineRetry: "Tekrar dene",

  installTitle: "Panoyu ana ekraniniza ekleyin",
  installBody:
    "Analitiginizi telefonunuzdan tek dokunusla acin. Tarayicidan kullanmaya devam edebilirsiniz — bir sey degismez.",
  installAdd: "Ekle",
  installDismiss: "Kapat",

  loading: "Yukleniyor...",
  busy: "...",
} as const;

export type AppStrings = typeof appStrings;

export const appDict: Dict<Record<keyof AppStrings, string>> = {
  tr: appStrings,
  en: {
    navSignIn: "Sign in",
    navSignUp: "Sign up",
    langLabel: "Language",

    heroBadge: "Live · served from the edge",
    heroTitleBefore: "Your media kit, one link ",
    heroTitleAccent: "ready",
    heroTitleAfter: " to send.",
    heroBody:
      "Bring your reach, engagement, audience breakdown and past brand work together on one polished page, then share it.",
    heroSeeDemo: "See an example kit",
    heroBrowseDemo: "Explore the demo",

    featureStatsTitle: "Reach & engagement",
    featureStatsBody:
      "Followers, average views and a platform-specific engagement rate for each channel — with trend badges.",
    featureAudienceTitle: "Audience breakdown",
    featureAudienceBody: "Age, gender and country split, so a brand can see exactly who you reach.",
    featureCollabsTitle: "Brand collaborations",
    featureCollabsBody: "Past campaigns and how they performed — social proof in one place.",

    footer: "A free media-kit tool — every feature open to everyone.",

    loginTitle: "Sign in",
    loginSubtitle: "Get back to your dashboard.",
    loginEmail: "Email",
    loginPassword: "Password",
    loginSubmit: "Sign in",
    loginOr: "or",
    loginDemo: "Explore the demo",
    loginDemoHint: "Browse the dashboard on a fully populated account (resets nightly).",
    loginNoAccount: "Don't have an account?",
    loginSignUp: "Sign up",
    loginFailed: "Sign-in failed — check your email and password.",
    loginThrottled: "Too many attempts. Please wait a moment.",
    loginUnreachable: "Couldn't reach the server.",

    registerTitle: "Create an account",
    registerSubtitle: "Get your media kit live in minutes.",
    registerName: "Display name",
    registerSubmit: "Create account",
    registerHaveAccount: "Already have an account?",
    registerSignIn: "Sign in",
    registerPasswordHint: "At least 8 characters",
    registerEmailTaken: "That email is already registered.",
    registerInvalid: "Please check your details — the password needs at least 8 characters.",
    registerFailed: "Couldn't create the account.",

    offlineTitle: "No connection",
    offlineBody:
      "Everything on your dashboard is read live from the server, so there is nothing meaningful to show offline. You can pick up where you left off once you're back online.",
    offlineRetry: "Try again",

    installTitle: "Add the dashboard to your home screen",
    installBody:
      "Open your analytics from your phone in one tap. You can keep using it in the browser — nothing changes.",
    installAdd: "Add",
    installDismiss: "Dismiss",

    loading: "Loading...",
    busy: "...",
  },
};
