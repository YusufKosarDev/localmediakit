import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PasswordGate from "@/app/[slug]/PasswordGate";
import { makeKit } from "./fixtures";

// The gate is the ONE place kit content is fetched per-request instead of from
// the edge. A regression here either leaks a protected kit or locks out the
// right password — both worth a test.
describe("PasswordGate (protected kit unlock)", () => {
  it("keeps the kit hidden and explains a wrong password", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 401 })
    );

    render(<PasswordGate slug="gizli" title="Gizli Kit" theme="light" />);
    await userEvent.type(screen.getByPlaceholderText("Sifre"), "yanlis");
    await userEvent.click(screen.getByRole("button", { name: "Goruntule" }));

    expect(await screen.findByText("Sifre yanlis.")).toBeInTheDocument();
    // The unlocked content must never appear on a failed attempt.
    expect(screen.queryByRole("heading", { name: "Ayse Gezgin" })).not.toBeInTheDocument();
  });

  it("reveals the kit once the backend accepts the password", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(makeKit({ title: "Ayse Gezgin" })), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<PasswordGate slug="gizli" title="Gizli Kit" theme="light" />);
    await userEvent.type(screen.getByPlaceholderText("Sifre"), "dogru-sifre");
    await userEvent.click(screen.getByRole("button", { name: "Goruntule" }));

    // The shared KitCard now renders the unlocked snapshot.
    expect(await screen.findByRole("heading", { name: "Ayse Gezgin" })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Sifre")).not.toBeInTheDocument();
  });

  it("surfaces the rate-limit message on 429", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 429 })
    );

    render(<PasswordGate slug="gizli" title="Gizli Kit" theme="light" />);
    await userEvent.type(screen.getByPlaceholderText("Sifre"), "x");
    await userEvent.click(screen.getByRole("button", { name: "Goruntule" }));

    expect(await screen.findByText(/Cok fazla deneme/)).toBeInTheDocument();
  });
});
