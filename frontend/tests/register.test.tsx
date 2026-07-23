import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import RegisterPage from "@/app/register/page";

// Register maps distinct backend statuses to distinct, specific messages. A
// generic "failed" for every case would be a real UX regression, so pin the
// mapping.
describe("RegisterPage error mapping", () => {
  async function fillAndSubmit() {
    await userEvent.type(screen.getByLabelText("Ad"), "Ayse");
    await userEvent.type(screen.getByLabelText("Email"), "ayse@ornek.com");
    await userEvent.type(screen.getByLabelText("Sifre"), "sifre123");
    await userEvent.click(screen.getByRole("button", { name: "Kayit ol" }));
  }

  it("tells the visitor when the email is already taken (409)", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 409 }));

    render(<RegisterPage />);
    await fillAndSubmit();

    expect(await screen.findByText(/zaten kayitli/)).toBeInTheDocument();
  });

  it("explains invalid input (400) rather than a generic failure", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 400 }));

    render(<RegisterPage />);
    await fillAndSubmit();

    expect(await screen.findByText(/Gecersiz bilgi/)).toBeInTheDocument();
    expect(screen.queryByText("Kayit basarisiz.")).not.toBeInTheDocument();
  });
});
