import type { Dict } from "./index";

/**
 * Landing, auth and the standalone app surfaces.
 *
 * <p>Separate from the dashboard's dictionary so a visitor who only ever sees
 * the marketing page does not download several hundred dashboard strings, and
 * separate from the public kit page's for the same reason.
 */
const appStrings = {
  navSignIn: "Giriş",
  navSignUp: "Kayıt ol",
  langLabel: "Dil",

  heroBadge: "Canlı · edge'den servis edilir",
  heroTitleBefore: "Medya kitiniz, markaya ",
  heroTitleAccent: "hazır",
  heroTitleAfter: " bir link.",
  heroBody:
    "İstatistik, etkileşim oranı, demografi ve marka iş birliklerini tek, şık bir sayfada toplayın ve markalarla paylaşın.",
  heroSeeDemo: "Örnek medya kitini gör",
  heroBrowseDemo: "Demo olarak gez",

  featureStatsTitle: "İstatistik & etkileşim",
  featureStatsBody:
    "Platform başına takipçi, ortalama izlenme ve platforma özel etkileşim oranı — trend rozetleriyle.",
  featureAudienceTitle: "Kitle demografisi",
  featureAudienceBody: "Yaş, cinsiyet ve ülke dağılımı; markaya kime ulaştığını net gösterir.",
  featureCollabsTitle: "Marka iş birlikleri",
  featureCollabsBody: "Geçmiş kampanyalar ve sonuçlarıyla sosyal kanıt vitrini.",

  footer: "Ücretsiz medya kiti aracı — tüm özellikler herkese açık.",

  loginTitle: "Giriş yap",
  loginSubtitle: "Panonuza erişin.",
  loginEmail: "E-posta",
  loginPassword: "Şifre",
  loginSubmit: "Giriş yap",
  loginOr: "veya",
  loginDemo: "Demo olarak gez",
  loginDemoHint: "Dolu bir hesapla panoyu keşfedin (saat başı sıfırlanır).",
  forgotLink: "Şifremi unuttum",
  forgotTitle: "Şifre sıfırlama",
  forgotSubtitle: "Hesabınızın e-posta adresini girin; kayıtlıysa bir sıfırlama linki gönderilir.",
  forgotSubmit: "Sıfırlama linki gönder",
  forgotSent: "Bu adres kayıtlıysa bir sıfırlama linki gönderildi. Gelen kutunuzu kontrol edin.",
  forgotBackToLogin: "Girişe dön",
  resetTitle: "Yeni şifre belirleyin",
  resetPassword: "Yeni şifre (en az 8 karakter)",
  resetSubmit: "Şifreyi değiştir",
  resetDone: "Şifreniz değiştirildi. Şimdi giriş yapabilirsiniz.",
  resetInvalid: "Bu link geçersiz veya süresi dolmuş. Yeni bir tane isteyin.",
  resetThrottled: "Çok fazla deneme. Lütfen biraz bekleyin.",
  loginNoAccount: "Hesabın yok mu?",
  loginSignUp: "Kayıt ol",
  loginFailed: "Giriş başarısız (e-posta/şifre hatalı).",
  loginThrottled: "Çok fazla deneme. Lütfen biraz bekleyin.",
  loginUnreachable: "Sunucuya ulaşılamadı.",

  registerTitle: "Hesap oluştur",
  registerSubtitle: "İlk medya kitinizi dakikalar içinde yayınlayın.",
  registerName: "Görünen ad",
  registerSubmit: "Kayıt ol",
  registerHaveAccount: "Zaten hesabın var mı?",
  registerSignIn: "Giriş yap",
  registerPasswordHint: "En az 8 karakter",
  registerEmailTaken: "Bu e-posta zaten kayıtlı.",
  registerInvalid: "Bilgileri kontrol edin (şifre en az 8 karakter).",
  registerFailed: "Kayıt oluşturulamadı.",

  confirmEmailTitle: "Yeni adresinizi doğrulayın",
  confirmEmailWorking: "Doğrulanıyor...",
  confirmEmailDone: "Adresiniz güncellendi. Artık yeni e-postanızla giriş yapabilirsiniz.",
  confirmEmailInvalid: "Bu link geçersiz veya süresi dolmuş. Ayarlar'dan değişikliği tekrar isteyin.",

  offlineTitle: "Bağlantı yok",
  offlineBody:
    "Panonuzdaki her şey sunucudan canlı okunur, bu yüzden çevrimdışı gösterilebilecek bir içerik yok. Bağlantınız gelince kaldığınız yerden devam edebilirsiniz.",
  offlineRetry: "Tekrar dene",

  installTitle: "Panoyu ana ekranınıza ekleyin",
  installBody:
    "Analitiğinizi telefonunuzdan tek dokunuşla açın. Tarayıcıdan kullanmaya devam edebilirsiniz — bir şey değişmez.",
  installAdd: "Ekle",
  installDismiss: "Kapat",

  loading: "Yükleniyor...",
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
    loginDemoHint: "Browse the dashboard on a fully populated account (resets hourly).",
    forgotLink: "Forgot your password?",
    forgotTitle: "Reset your password",
    forgotSubtitle: "Enter your account email. If it is registered, a reset link is on its way.",
    forgotSubmit: "Send reset link",
    forgotSent: "If that address is registered, a reset link has been sent. Check your inbox.",
    forgotBackToLogin: "Back to sign in",
    resetTitle: "Choose a new password",
    resetPassword: "New password (at least 8 characters)",
    resetSubmit: "Change password",
    resetDone: "Your password has been changed. You can sign in now.",
    resetInvalid: "This link is invalid or has expired. Please request a new one.",
    resetThrottled: "Too many attempts. Please wait a moment.",
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

    confirmEmailTitle: "Confirm your new address",
    confirmEmailWorking: "Confirming...",
    confirmEmailDone: "Your address has been updated. You can now sign in with your new email.",
    confirmEmailInvalid: "This link is invalid or has expired. Ask for the change again from Settings.",

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
