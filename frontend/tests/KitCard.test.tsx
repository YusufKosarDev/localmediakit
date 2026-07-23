import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import KitCard from "@/app/[slug]/KitCard";
import { makeKit } from "./fixtures";

// The public [slug] page IS the product's shop window: a frozen snapshot a
// brand reads. These assert the numbers and plan/preview rules a visitor sees.
describe("KitCard (public snapshot page)", () => {
  it("renders the snapshot's platform stats a brand cares about", () => {
    render(<KitCard kit={makeKit()} />);

    // Title + the section a brand scans first.
    expect(screen.getByRole("heading", { name: "Ayse Gezgin", level: 1 })).toBeInTheDocument();
    expect(screen.getByText("Instagram")).toBeInTheDocument();
    // Engagement rate is formatted tr-TR with the percent sign in front (%6,47),
    // not the raw 6.47.
    expect(screen.getByText("%6,47")).toBeInTheDocument();
    expect(screen.getByText("etkilesim")).toBeInTheDocument();
    // Collaborations and rate card came from the same frozen snapshot.
    expect(screen.getByText("Kahve Dunyasi")).toBeInTheDocument();
    expect(screen.getByText("Calisma Ucretleri")).toBeInTheDocument();
  });

  it("shows a positive 30-day growth badge with a + sign, negative without", () => {
    // The badge's own text (+ / number / %) reads as one string; the "30g"
    // suffix sits in a nested span, so it is excluded by getByText.
    const { unmount } = render(<KitCard kit={makeKit()} />); // +12.5
    expect(screen.getByText("+12,5%")).toBeInTheDocument();
    unmount();

    const down = makeKit();
    down.platforms[0].followerGrowth30d = -8.2;
    render(<KitCard kit={down} />);
    // A falling badge keeps the minus and never prints a leading "+".
    expect(screen.getByText("-8,2%")).toBeInTheDocument();
    expect(screen.queryByText("+8,2%")).not.toBeInTheDocument();
  });

  it("enforces the plan badge rule: 'LocalMediaKit' shows for FREE, hidden for PRO", () => {
    const { unmount } = render(<KitCard kit={makeKit({ showBadge: true })} />);
    expect(screen.getByText("LocalMediaKit")).toBeInTheDocument();
    unmount();

    render(<KitCard kit={makeKit({ showBadge: false })} />);
    expect(screen.queryByText("LocalMediaKit")).not.toBeInTheDocument();
  });

  it("in preview mode: banners the draft, hides the contact form, dates it as unpublished", () => {
    render(<KitCard kit={makeKit({ contactEnabled: true })} preview />);

    expect(screen.getByText(/ONIZLEME/)).toBeInTheDocument();
    expect(screen.getByText(/henuz yayinlanmadi/)).toBeInTheDocument();
    // Contact form is deliberately absent on previews even when enabled.
    expect(screen.queryByPlaceholderText(/Marka \/ sirket adi/)).not.toBeInTheDocument();
  });

  it("tolerates an older snapshot: null rate card and empty lists render no ghost sections", () => {
    const legacy = makeKit({
      rateCard: null,
      demographics: [],
      collaborations: [],
      platforms: [],
    });
    // Must not throw on the absent rate card (older snapshots predate it).
    render(<KitCard kit={legacy} />);

    // The title still renders; empty sections leave no dangling headers.
    expect(screen.getByRole("heading", { name: "Ayse Gezgin" })).toBeInTheDocument();
    expect(screen.queryByText("Calisma Ucretleri")).not.toBeInTheDocument();
    expect(screen.queryByText("Marka Isbirlikleri")).not.toBeInTheDocument();
    expect(screen.queryByText("Platformlar")).not.toBeInTheDocument();
  });
});
