import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  WelcomeTour, OnboardingChecklist, EmptyKitState, useTourVisibility,
  type OnboardingState,
} from "@/app/dashboard/_Onboarding";

const base: OnboardingState = {
  dismissed: false,
  hasKit: false,
  hasStats: false,
  hasPublished: false,
  publicSlug: null,
};

/** Exercises the visibility hook the dashboard uses to decide on the tour. */
function TourHarness({ state, demoKey }: { state: OnboardingState | null; demoKey: string | null }) {
  const tour = useTourVisibility(state, demoKey);
  return (
    <div>
      <span data-testid="open">{tour.open ? "acik" : "kapali"}</span>
      <button onClick={tour.close}>kapat</button>
    </div>
  );
}

describe("WelcomeTour", () => {
  it("leads with the publish rule, the one concept the product hides", async () => {
    render(<WelcomeTour onClose={() => {}} />);

    // Slide 1 introduces the product...
    expect(screen.getByText(/hos geldiniz/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Devam/ }));

    // ...and slide 2 is the draft/live distinction, before any confusion.
    expect(screen.getByText(/En onemlisi: Yayinla/)).toBeInTheDocument();
    expect(
      screen.getByText(/Public sayfaniza ancak Yayinla dediginizde yansir/)
    ).toBeInTheDocument();
  });

  it("can always be skipped without walking through the slides", async () => {
    const onClose = vi.fn();
    render(<WelcomeTour onClose={onClose} />);

    await userEvent.click(screen.getByRole("button", { name: "Atla" }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("closes on Escape", async () => {
    const onClose = vi.fn();
    render(<WelcomeTour onClose={onClose} />);

    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("walks forward and back through every slide", async () => {
    render(<WelcomeTour onClose={() => {}} />);

    expect(screen.getByText("1 / 4")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Devam/ }));
    await userEvent.click(screen.getByRole("button", { name: /Devam/ }));
    expect(screen.getByText("3 / 4")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Geri/ }));
    expect(screen.getByText("2 / 4")).toBeInTheDocument();
  });

  it("ends with a finish action rather than another Devam", async () => {
    const onClose = vi.fn();
    render(<WelcomeTour onClose={onClose} />);

    for (let i = 0; i < 3; i++) {
      await userEvent.click(screen.getByRole("button", { name: /Devam/ }));
    }
    await userEvent.click(screen.getByRole("button", { name: "Basla" }));
    expect(onClose).toHaveBeenCalledOnce();
  });
});

describe("useTourVisibility", () => {
  beforeEach(() => localStorage.clear());

  it("opens for a brand new account", () => {
    render(<TourHarness state={base} demoKey={null} />);
    expect(screen.getByTestId("open")).toHaveTextContent("acik");
  });

  it("stays shut once the account has dismissed it", () => {
    render(<TourHarness state={{ ...base, dismissed: true }} demoKey={null} />);
    expect(screen.getByTestId("open")).toHaveTextContent("kapali");
  });

  it("does not interrupt someone who already built a kit", () => {
    // They are past the empty dashboard; the checklist guides them instead.
    render(<TourHarness state={{ ...base, hasKit: true }} demoKey={null} />);
    expect(screen.getByTestId("open")).toHaveTextContent("kapali");
  });

  it("waits for the state to arrive before deciding", () => {
    render(<TourHarness state={null} demoKey={null} />);
    expect(screen.getByTestId("open")).toHaveTextContent("kapali");
  });

  it("does not reopen for a demo visitor who already skipped it in this browser", () => {
    // The demo never stores a dismissal server-side, so the browser flag is
    // what stops it reappearing on every reload for one visitor.
    localStorage.setItem("lmk.tour.demo", "1");
    render(<TourHarness state={base} demoKey="lmk.tour.demo" />);
    expect(screen.getByTestId("open")).toHaveTextContent("kapali");
  });

  it("greets a demo visitor who has not seen it, and remembers the skip", async () => {
    render(<TourHarness state={base} demoKey="lmk.tour.demo" />);
    expect(screen.getByTestId("open")).toHaveTextContent("acik");

    await userEvent.click(screen.getByRole("button", { name: "kapat" }));
    expect(screen.getByTestId("open")).toHaveTextContent("kapali");
    expect(localStorage.getItem("lmk.tour.demo")).toBe("1");
  });
});

describe("OnboardingChecklist", () => {
  it("counts only the steps the account has actually done", () => {
    render(
      <OnboardingChecklist
        state={{ ...base, hasKit: true }}
        onStartFirstKit={() => {}}
        onDismiss={() => {}}
      />
    );

    expect(screen.getByText("1 / 3 tamamlandi")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "1");
    // The publish hint stays visible while it is still outstanding.
    expect(screen.getByText(/ancak Yayinla ile yansir/)).toBeInTheDocument();
  });

  it("shows the resulting public link once everything is done", () => {
    render(
      <OnboardingChecklist
        state={{ dismissed: false, hasKit: true, hasStats: true, hasPublished: true, publicSlug: "ayse" }}
        onStartFirstKit={() => {}}
        onDismiss={() => {}}
      />
    );

    expect(screen.getByText("Hazirsiniz")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /\/ayse adresini gor/ })).toHaveAttribute(
      "href",
      "/ayse"
    );
  });

  it("can be hidden at any point", async () => {
    const onDismiss = vi.fn();
    render(
      <OnboardingChecklist state={base} onStartFirstKit={() => {}} onDismiss={onDismiss} />
    );

    await userEvent.click(screen.getByRole("button", { name: "Gizle" }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});

describe("EmptyKitState", () => {
  it("explains what a media kit is instead of showing an empty list", () => {
    render(<EmptyKitState onStart={() => {}} onQuickStart={() => {}} quickStartBusy={false} />);

    expect(screen.getByText("Henuz bir medya kitiniz yok")).toBeInTheDocument();
    expect(screen.getByText(/markalara tek bir linkle gosterdiginiz sayfadir/)).toBeInTheDocument();
  });

  it("offers both an empty start and a sample-content start", async () => {
    const onStart = vi.fn();
    const onQuickStart = vi.fn();
    render(
      <EmptyKitState onStart={onStart} onQuickStart={onQuickStart} quickStartBusy={false} />
    );

    await userEvent.click(screen.getByRole("button", { name: "Bos kit olustur" }));
    await userEvent.click(screen.getByRole("button", { name: "Ornek icerikle basla" }));
    expect(onStart).toHaveBeenCalledOnce();
    expect(onQuickStart).toHaveBeenCalledOnce();
  });

  it("locks the sample start while it is running so it cannot double-fire", () => {
    render(<EmptyKitState onStart={() => {}} onQuickStart={() => {}} quickStartBusy />);
    expect(screen.getByRole("button", { name: "Hazirlaniyor..." })).toBeDisabled();
  });
});
