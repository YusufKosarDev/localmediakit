import { describe, it, expect, vi, beforeAll, afterAll, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import SettingsPage from "@/app/dashboard/settings/page";

const ME = {
  id: 1,
  email: "ayse@ornek.com",
  displayName: "Ayse",
  avatarUrl: null,
  theme: "LIGHT",
  plan: "PRO",
  leadNotificationsEnabled: true,
  locale: "tr",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Answers GET /api/me, then defers to `then` for the action under test. */
function mockFetch(then: (url: string, init?: RequestInit) => Response) {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
    const url = typeof input === "string" ? input : String(input);
    const method = (init?.method ?? "GET").toUpperCase();
    if (url.endsWith("/api/me") && method === "GET") return json(ME);
    return then(url, init);
  });
}

// The settings page holds the destructive controls, so these tests pin the
// guards: the delete button stays disabled until both proofs are supplied, a
// mistyped new password never reaches the network, and a changed email swaps
// in the replacement token instead of leaving a dead one behind.
describe("SettingsPage", () => {
  let realLocation: Location;

  beforeAll(() => {
    realLocation = window.location;
    Object.defineProperty(window, "location", {
      configurable: true,
      writable: true,
      value: { href: "" },
    });
  });

  afterAll(() => {
    Object.defineProperty(window, "location", { configurable: true, value: realLocation });
  });

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem("token", "jwt-old");
  });

  afterEach(() => vi.restoreAllMocks());

  it("keeps the delete button disabled until the password and the exact phrase are given", async () => {
    mockFetch(() => json({}, 204));
    render(<SettingsPage />);

    const button = await screen.findByRole("button", { name: /Hesabimi kalici olarak sil/ });
    expect(button).toBeDisabled();

    // Password alone is not enough.
    await userEvent.type(screen.getByLabelText("Mevcut sifre", { selector: "#deletePassword" }), "gizli123");
    expect(button).toBeDisabled();

    // Neither is a near-miss of the confirmation phrase.
    const confirmation = screen.getByLabelText(/Onaylamak icin/);
    await userEvent.type(confirmation, "hesabimi sil");
    expect(button).toBeDisabled();

    await userEvent.clear(confirmation);
    await userEvent.type(confirmation, "HESABIMI SIL");
    expect(button).toBeEnabled();
  });

  it("rejects a mismatched new password without calling the backend", async () => {
    const fetchSpy = mockFetch(() => json({}, 204));
    render(<SettingsPage />);

    await screen.findByText("Sifre degistir");
    await userEvent.type(screen.getByLabelText("Mevcut sifre", { selector: "#currentPassword" }), "eskisifre");
    await userEvent.type(screen.getByLabelText("Yeni sifre"), "yenisifre1");
    await userEvent.type(screen.getByLabelText("Yeni sifre (tekrar)"), "yenisifre2");
    await userEvent.click(screen.getByRole("button", { name: "Sifreyi degistir" }));

    expect(await screen.findByText("Yeni sifreler eslesmiyor.")).toBeInTheDocument();
    // Only the initial GET /api/me happened.
    const posts = fetchSpy.mock.calls.filter(
      ([, init]) => (init as RequestInit | undefined)?.method === "POST"
    );
    expect(posts).toHaveLength(0);
  });

  it("swaps in the replacement token after an email change", async () => {
    mockFetch((url, init) => {
      if (url.endsWith("/api/me/email") && init?.method === "POST") {
        return json({ token: "jwt-new", user: { ...ME, email: "yeni@ornek.com" } });
      }
      return json({}, 404);
    });
    render(<SettingsPage />);

    await screen.findByText("E-posta degistir");
    await userEvent.type(screen.getByLabelText("Yeni e-posta"), "yeni@ornek.com");
    await userEvent.type(screen.getByLabelText("Mevcut sifre", { selector: "#emailPassword" }), "gizli123");
    await userEvent.click(screen.getByRole("button", { name: "E-postayi degistir" }));

    // The old token names the old address and no longer resolves; without the
    // swap the user would be silently signed out on the next request.
    await vi.waitFor(() => expect(localStorage.getItem("token")).toBe("jwt-new"));
    expect(await screen.findByText("E-postaniz guncellendi.")).toBeInTheDocument();
  });

  it("surfaces the backend message when an email is already taken", async () => {
    mockFetch((url, init) => {
      if (url.endsWith("/api/me/email") && init?.method === "POST") {
        return json({ status: 409, error: "Bu e-posta baska bir hesapta kayitli." }, 409);
      }
      return json({}, 404);
    });
    render(<SettingsPage />);

    await screen.findByText("E-posta degistir");
    await userEvent.type(screen.getByLabelText("Yeni e-posta"), "dolu@ornek.com");
    await userEvent.type(screen.getByLabelText("Mevcut sifre", { selector: "#emailPassword" }), "gizli123");
    await userEvent.click(screen.getByRole("button", { name: "E-postayi degistir" }));

    expect(
      await screen.findByText("Bu e-posta baska bir hesapta kayitli.")
    ).toBeInTheDocument();
    // A rejected change must not disturb the working session.
    expect(localStorage.getItem("token")).toBe("jwt-old");
  });

  it("saves the account language, and says kits carry their own", async () => {
    const fetchSpy = mockFetch((url, init) => {
      if (url.endsWith("/api/me") && init?.method === "PUT") return json({ ...ME, locale: "en" });
      return json({}, 404);
    });
    render(<SettingsPage />);

    const select = await screen.findByLabelText("Arayuz dili");
    expect(select).toHaveValue("tr");

    // The account language is the dashboard's; a published kit's language is
    // a separate, per-kit field, and the copy has to say so.
    expect(screen.getByText(/her kit kendi sunum dilini/)).toBeInTheDocument();

    await userEvent.selectOptions(select, "en");

    // Picking a language switches the page immediately, before saving — the
    // button itself is the proof.
    await userEvent.click(await screen.findByRole("button", { name: "Save language" }));

    await vi.waitFor(() => {
      const put = fetchSpy.mock.calls.find(
        ([, init]) => (init as RequestInit | undefined)?.method === "PUT"
      );
      expect(JSON.parse(String((put?.[1] as RequestInit).body))).toMatchObject({ locale: "en" });
    });
  });

  it("turns lead emails off and says the leads themselves keep arriving", async () => {
    const fetchSpy = mockFetch((url, init) => {
      if (url.endsWith("/api/me") && init?.method === "PUT") {
        return json({ ...ME, leadNotificationsEnabled: false });
      }
      return json({}, 404);
    });
    render(<SettingsPage />);

    const toggle = await screen.findByLabelText(/Yeni marka teklifi e-postasi/);
    expect(toggle).toBeChecked();

    // Switching notifications off must not read as "stop receiving offers".
    expect(
      screen.getByText(/teklifler yine de Gelen Kutusu sekmenize duser/)
    ).toBeInTheDocument();

    await userEvent.click(toggle);
    await userEvent.click(screen.getByRole("button", { name: "Bildirim tercihini kaydet" }));

    await vi.waitFor(() => {
      const put = fetchSpy.mock.calls.find(
        ([, init]) => (init as RequestInit | undefined)?.method === "PUT"
      );
      expect(JSON.parse(String((put?.[1] as RequestInit).body))).toMatchObject({
        leadNotificationsEnabled: false,
      });
    });
  });

  it("saves the profile and states that kits are unaffected", async () => {
    const fetchSpy = mockFetch((url, init) => {
      if (url.endsWith("/api/me") && init?.method === "PUT") {
        return json({ ...ME, displayName: "Ayse Yilmaz", theme: "DARK" });
      }
      return json({}, 404);
    });
    render(<SettingsPage />);

    // The profile/kit boundary is a promise to the user, so it is on screen.
    expect(
      await screen.findByText(/Medya kitlerinizin kendi basligi ve/)
    ).toBeInTheDocument();

    const name = screen.getByLabelText("Gorunen ad");
    await userEvent.clear(name);
    await userEvent.type(name, "Ayse Yilmaz");
    await userEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    expect(await screen.findByText("Profil guncellendi.")).toBeInTheDocument();
    const put = fetchSpy.mock.calls.find(
      ([, init]) => (init as RequestInit | undefined)?.method === "PUT"
    );
    expect(JSON.parse(String((put?.[1] as RequestInit).body))).toMatchObject({
      displayName: "Ayse Yilmaz",
    });
  });
});
