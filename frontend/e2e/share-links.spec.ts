import { test, expect } from "@playwright/test";
import { BACKEND, createKit, publish, registerAccount, signIn, skipOnboarding } from "./support";

/**
 * The product's own premise, end to end: send a brand a link, and find out they
 * read it.
 *
 * <p>This needs a real browser rather than a component test. The token lives in
 * the query string of a page that is force-static and shared by every visitor,
 * so it is read client-side and handed to a fire-and-forget beacon -- three
 * moving parts, none of which the page reacts to if they fail. Only the
 * creator's own view of the link can say whether any of it worked.
 */
test.describe("per-brand share links", () => {
  async function createShareLink(
    request: Parameters<typeof registerAccount>[0],
    account: { token: string },
    kitId: number,
    label: string
  ) {
    const response = await request.post(`${BACKEND}/api/mediakits/${kitId}/share-links`, {
      headers: { Authorization: `Bearer ${account.token}` },
      data: { label },
    });
    expect(response.status(), `create share link ${label}`).toBe(201);
    return (await response.json()) as { id: number; token: string; url: string };
  }

  async function shareLinks(
    request: Parameters<typeof registerAccount>[0],
    account: { token: string },
    kitId: number
  ) {
    const response = await request.get(`${BACKEND}/api/mediakits/${kitId}/share-links`, {
      headers: { Authorization: `Bearer ${account.token}` },
    });
    return (await response.json()) as Array<{ label: string; views: number; active: boolean }>;
  }

  test("a brand opening its own link shows up against that brand", async ({ page, request }) => {
    const account = await registerAccount(request, "share");
    await skipOnboarding(request, account);
    const kit = await createKit(request, account, "Paylasim Kiti");
    await publish(request, account, kit.id);
    const nike = await createShareLink(request, account, kit.id, "Nike");
    const adidas = await createShareLink(request, account, kit.id, "Adidas");

    // Only one of them opens it.
    await page.goto(nike.url);
    await expect(page.getByRole("heading", { name: "Paylasim Kiti" })).toBeVisible();

    await expect
      .poll(
        async () => {
          const links = await shareLinks(request, account, kit.id);
          return links.find((l) => l.label === "Nike")?.views ?? 0;
        },
        { timeout: 20_000, message: "the visit was never attributed to the link" }
      )
      .toBe(1);

    // The other brand's link must not have moved. A feature that reports the
    // wrong brand looked is worse than one that reports nothing.
    const links = await shareLinks(request, account, kit.id);
    expect(links.find((l) => l.label === "Adidas")?.views).toBe(0);
    expect(adidas.token).not.toBe(nike.token);
  });

  test("the same page opened without a token is counted but not attributed", async ({
    page,
    request,
  }) => {
    const account = await registerAccount(request, "share-plain");
    await skipOnboarding(request, account);
    const kit = await createKit(request, account, "Etiketsiz Kit");
    await publish(request, account, kit.id);
    await createShareLink(request, account, kit.id, "Marka");

    await page.goto(`/${kit.slug}`);
    await expect(page.getByRole("heading", { name: "Etiketsiz Kit" })).toBeVisible();

    await expect
      .poll(
        async () => {
          const response = await request.get(`${BACKEND}/api/mediakits/${kit.id}/analytics`, {
            headers: { Authorization: `Bearer ${account.token}` },
          });
          return (await response.json()).totalViews as number;
        },
        { timeout: 20_000, message: "the plain visit was never counted" }
      )
      .toBe(1);

    const links = await shareLinks(request, account, kit.id);
    expect(links[0].views).toBe(0);
  });

  test("the creator can produce and revoke a link from the dashboard", async ({ page, request }) => {
    const account = await registerAccount(request, "share-ui");
    await skipOnboarding(request, account);
    const kit = await createKit(request, account, "Panel Kiti");
    await publish(request, account, kit.id);

    await signIn(page, account);
    await page.goto("/dashboard");
    await page.getByRole("button", { name: "Analitik" }).click();

    await page.getByPlaceholder("Marka adi").fill("Zara");
    await page.getByRole("button", { name: "Link uret" }).click();
    await expect(page.getByText("Zara")).toBeVisible();

    await page.getByRole("button", { name: "Iptal et" }).click();
    await expect(page.getByText("iptal edildi")).toBeVisible();

    // Revoked, not deleted: the row stays so its history stays with it.
    const links = await shareLinks(request, account, kit.id);
    expect(links).toHaveLength(1);
    expect(links[0].active).toBe(false);
  });
});
