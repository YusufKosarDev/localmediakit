import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Home from "@/app/page";
import LoginPage from "@/app/login/page";
import { LOCALE_STORAGE_KEY } from "@/app/_i18n/useLocale";
import { ERROR_CODES, translateError } from "@/app/_i18n/errors";
import { LOCALES } from "@/app/_i18n";

/**
 * Signed-out surfaces have no account to read a language from, so the choice
 * lives in the browser. These pin the two things that matter: the switch
 * actually changes the copy, and it survives a reload.
 */
describe("signed-out language switch", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it("renders the landing in Turkish by default", () => {
    render(<Home />);
    expect(screen.getByText(/Ornek medya kitini gor/)).toBeInTheDocument();
  });

  it("switches the landing to English and remembers it", async () => {
    render(<Home />);

    await userEvent.click(screen.getByRole("button", { name: "en" }));

    expect(await screen.findByText("See an example kit")).toBeInTheDocument();
    expect(screen.queryByText(/Ornek medya kitini gor/)).not.toBeInTheDocument();
    // Persisted, so the next page (and the next visit) opens in English.
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("en");
  });

  it("picks up a stored preference on a different page", async () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, "en");
    render(<LoginPage />);

    // The static HTML is Turkish; the stored choice is applied on mount.
    expect(await screen.findByRole("button", { name: "Sign in" })).toBeInTheDocument();
  });

  it("ignores a stored value it does not recognise", async () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, "klingon");
    render(<LoginPage />);

    expect(await screen.findByRole("button", { name: "Giris yap" })).toBeInTheDocument();
  });
});

/**
 * The backend sends a machine code beside its own message, so errors can be
 * read in the user's language without the server knowing what that is.
 */
describe("backend error translation", () => {
  it("translates a known code", () => {
    expect(translateError("EMAIL_ALREADY_USED", "Bu e-posta baska bir hesapta kayitli.", "en"))
      .toBe("That email is already registered to another account.");
  });

  it("falls back to the API's own message for an unknown code", () => {
    // This is what keeps the change additive: a code the client has never
    // heard of still shows the user something.
    expect(translateError("SOMETHING_NEW", "Beklenmeyen bir hata.", "en"))
      .toBe("Beklenmeyen bir hata.");
  });

  it("returns null when there is neither a known code nor a message", () => {
    expect(translateError(undefined, undefined, "en")).toBeNull();
    expect(translateError(undefined, "   ", "en")).toBeNull();
  });

  it("keeps every code complete in every locale", () => {
    for (const [code, entry] of Object.entries(ERROR_CODES)) {
      for (const locale of LOCALES) {
        expect(entry[locale], `${code}.${locale}`).toBeTruthy();
      }
    }
  });
});
