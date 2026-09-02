import { DEFAULT_LOCALE, type Locale } from "./index";

/**
 * Backend error codes, translated on the client.
 *
 * <p>The API answers with both a machine `code` and a human `error` string.
 * Translating here rather than server-side keeps the backend out of the
 * presentation-language business entirely — it never has to know who is
 * reading, and adding a language costs it nothing.
 *
 * <p>An unknown code is not an error: the caller falls back to the message the
 * API sent. That is what makes this safe to extend one code at a time.
 */
const ERRORS: Record<string, Record<Locale, string>> = {
  EMAIL_ALREADY_USED: {
    tr: "Bu e-posta başka bir hesapta kayıtlı.",
    en: "That email is already registered to another account.",
  },
  INVALID_CREDENTIALS: {
    tr: "E-posta veya şifre hatalı.",
    en: "Incorrect email or password.",
  },
  VALIDATION_FAILED: {
    tr: "Girilen bilgiler geçersiz.",
    en: "Some of the details entered are not valid.",
  },
  MALFORMED_BODY: {
    tr: "İstek okunamadı.",
    en: "The request could not be read.",
  },
  PLAN_LIMIT_EXCEEDED: {
    tr: "Plan sınırına ulaştınız.",
    en: "You have reached your plan's limit.",
  },
  PROTECTED_ACCOUNT: {
    tr: "Bu işlem demo hesabında yapılamaz.",
    en: "This cannot be done on the demo account.",
  },
  INVALID_APPEARANCE: {
    tr: "Geçersiz görünüm seçimi.",
    en: "That appearance option is not available.",
  },
  UNSUPPORTED_LOCALE: {
    tr: "Desteklenmeyen dil.",
    en: "That language is not supported.",
  },
  RESERVED_SLUG: {
    tr: "Bu adres ayrılmış, başka bir tane seçin.",
    en: "That address is reserved — please choose another.",
  },
  MEDIA_KIT_NOT_FOUND: {
    tr: "Medya kiti bulunamadı.",
    en: "Media kit not found.",
  },
  SYNC_COOLDOWN: {
    tr: "Çok kısa arayla senkron. Biraz bekleyin.",
    en: "Synced too recently. Please wait a moment.",
  },
  SYNC_NOT_CONFIGURED: {
    tr: "Bu veri kaynağı şu anda kullanılamıyor.",
    en: "This data source is currently unavailable.",
  },
  EXTERNAL_ACCOUNT_NOT_FOUND: {
    tr: "Kanal bulunamadı. Adını kontrol edin.",
    en: "Channel not found. Please check the handle.",
  },
  DOMAIN_ALREADY_EXISTS: {
    tr: "Bu alan adı zaten ekli.",
    en: "That domain has already been added.",
  },
  INVALID_DOMAIN: {
    tr: "Geçersiz alan adı.",
    en: "That domain is not valid.",
  },
  INVALID_KIT_PASSWORD: {
    tr: "Şifre hatalı.",
    en: "Incorrect password.",
  },
  TOO_MANY_UNLOCK_ATTEMPTS: {
    tr: "Çok fazla deneme. Biraz bekleyin.",
    en: "Too many attempts. Please wait a moment.",
  },
};

/**
 * @param code    the API's machine code, if it sent one
 * @param message the API's own message, used when the code is unrecognised
 */
export function translateError(
  code: string | undefined,
  message: string | undefined,
  locale: Locale
): string | null {
  if (code) {
    const entry = ERRORS[code];
    if (entry) return entry[locale] ?? entry[DEFAULT_LOCALE];
  }
  return message && message.trim() ? message : null;
}

/** Exposed so a test can assert every code is complete in every locale. */
export const ERROR_CODES = ERRORS;
