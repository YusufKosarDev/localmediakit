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
    tr: "Bu e-posta baska bir hesapta kayitli.",
    en: "That email is already registered to another account.",
  },
  INVALID_CREDENTIALS: {
    tr: "E-posta veya sifre hatali.",
    en: "Incorrect email or password.",
  },
  VALIDATION_FAILED: {
    tr: "Girilen bilgiler gecersiz.",
    en: "Some of the details entered are not valid.",
  },
  MALFORMED_BODY: {
    tr: "Istek okunamadi.",
    en: "The request could not be read.",
  },
  PLAN_LIMIT_EXCEEDED: {
    tr: "Plan sinirina ulastiniz.",
    en: "You have reached your plan's limit.",
  },
  PROTECTED_ACCOUNT: {
    tr: "Bu islem demo hesabinda yapilamaz.",
    en: "This cannot be done on the demo account.",
  },
  INVALID_APPEARANCE: {
    tr: "Gecersiz gorunum secimi.",
    en: "That appearance option is not available.",
  },
  UNSUPPORTED_LOCALE: {
    tr: "Desteklenmeyen dil.",
    en: "That language is not supported.",
  },
  RESERVED_SLUG: {
    tr: "Bu adres ayrilmis, baska bir tane secin.",
    en: "That address is reserved — please choose another.",
  },
  MEDIA_KIT_NOT_FOUND: {
    tr: "Medya kiti bulunamadi.",
    en: "Media kit not found.",
  },
  SYNC_COOLDOWN: {
    tr: "Cok kisa arayla senkron. Biraz bekleyin.",
    en: "Synced too recently. Please wait a moment.",
  },
  SYNC_NOT_CONFIGURED: {
    tr: "Bu veri kaynagi su anda kullanilamiyor.",
    en: "This data source is currently unavailable.",
  },
  EXTERNAL_ACCOUNT_NOT_FOUND: {
    tr: "Kanal bulunamadi. Adi kontrol edin.",
    en: "Channel not found. Please check the handle.",
  },
  DOMAIN_ALREADY_EXISTS: {
    tr: "Bu alan adi zaten ekli.",
    en: "That domain has already been added.",
  },
  INVALID_DOMAIN: {
    tr: "Gecersiz alan adi.",
    en: "That domain is not valid.",
  },
  INVALID_KIT_PASSWORD: {
    tr: "Sifre hatali.",
    en: "Incorrect password.",
  },
  TOO_MANY_UNLOCK_ATTEMPTS: {
    tr: "Cok fazla deneme. Biraz bekleyin.",
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
