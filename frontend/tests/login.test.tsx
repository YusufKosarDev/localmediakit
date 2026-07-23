import { describe, it, expect, vi, beforeAll, afterAll, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import LoginPage from "@/app/login/page";

// Login stores a bearer token in localStorage and only then navigates. The
// tests below pin the two rules that matter: never store a token on failure,
// always store it on success.
describe("LoginPage", () => {
  let realLocation: Location;

  beforeAll(() => {
    realLocation = window.location;
    // Redirect after success sets window.location.href; give it a plain sink so
    // jsdom does not attempt a real navigation.
    Object.defineProperty(window, "location", {
      configurable: true,
      writable: true,
      value: { href: "" },
    });
  });

  afterAll(() => {
    Object.defineProperty(window, "location", { configurable: true, value: realLocation });
  });

  beforeEach(() => localStorage.clear());

  it("shows an error and stores no token when credentials are rejected", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 401 }));

    render(<LoginPage />);
    await userEvent.type(screen.getByLabelText("Email"), "yok@ornek.com");
    await userEvent.type(screen.getByLabelText("Sifre"), "kotusifre");
    await userEvent.click(screen.getByRole("button", { name: "Giris yap" }));

    expect(await screen.findByText(/Giris basarisiz/)).toBeInTheDocument();
    expect(localStorage.getItem("token")).toBeNull();
  });

  it("stores the returned token on a successful login", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ token: "jwt-abc" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<LoginPage />);
    await userEvent.type(screen.getByLabelText("Email"), "ayse@ornek.com");
    await userEvent.type(screen.getByLabelText("Sifre"), "doetrusifre");
    await userEvent.click(screen.getByRole("button", { name: "Giris yap" }));

    await vi.waitFor(() => expect(localStorage.getItem("token")).toBe("jwt-abc"));
  });
});
