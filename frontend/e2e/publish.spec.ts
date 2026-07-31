import { test, expect } from "@playwright/test";
import {
  BACKEND, addStat, createKit, expectPublicPageNotToShow, expectPublicPageToShow,
  publish, registerAccount, signIn, skipOnboarding,
} from "./support";

test.describe("publishing", () => {
  /**
   * The whole product in one pass, driven through the browser rather than the
   * API: an account with nothing in it ends with a public page a brand could
   * be sent.
   */
  test("a new account can build a kit and put it on the web", async ({ page, request }) => {
    const account = await registerAccount(request, "publish");
    await skipOnboarding(request, account);
    await signIn(page, account);

    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: "Yeni medya kiti" })).toBeVisible();

    await page.getByPlaceholder("Baslik *").fill("Gezgin Kanali");
    await page.getByRole("button", { name: "Olustur", exact: true }).click();

    // The kit appears in the list, as a draft.
    const card = page.locator("text=Gezgin Kanali").first();
    await expect(card).toBeVisible();
    await expect(page.getByText("DRAFT").first()).toBeVisible();

    // Add a measurement through the Stats tab.
    await page.getByRole("button", { name: "Istatistik & Kitle" }).click();
    // Wait for the panel to actually open before typing into it: the tab strip
    // is rendered whether or not a panel is expanded.
    await expect(page.getByText("Platform istatistikleri")).toBeVisible();

    await page.getByPlaceholder("takipci *").fill("42000");
    await page.getByRole("button", { name: "Olcum ekle" }).click();
    // The measurement lands in the list above the form.
    await expect(page.getByText("42.000", { exact: false })).toBeVisible();

    await page.getByRole("button", { name: "Yayinla" }).click();
    await expect(page.getByText("Yayinlandi.")).toBeVisible();

    // The public link is offered, and it serves the content.
    const link = page.locator('a[href^="/"]:has-text("/")').first();
    const slug = (await link.getAttribute("href"))!.replace("/", "");

    await expectPublicPageToShow(page, slug, "Gezgin Kanali");
    await expect(page.getByText("Platformlar", { exact: false })).toBeVisible();
  });

  /**
   * The claim the rest of the architecture is built around, proven in a
   * browser: a draft edit reaches the public page only when the owner
   * publishes. Every other guarantee (a brand never sees half-finished work,
   * a rollback restores exactly what was live) rests on this one.
   */
  test("a draft edit does not reach the public page until it is published", async ({
    page,
    request,
  }) => {
    const account = await registerAccount(request, "frozen");
    await skipOnboarding(request, account);
    const kit = await createKit(request, account, "ILK BASLIK");
    await publish(request, account, kit.id);

    await expectPublicPageToShow(page, kit.slug, "ILK BASLIK");

    // Edit the draft in the dashboard â and only the draft.
    await signIn(page, account);
    await page.goto("/dashboard");
    await page.getByRole("button", { name: "Duzenle" }).first().click();

    const title = page.getByLabel("Baslik").first();
    await title.fill("IKINCI BASLIK");
    await page.getByRole("button", { name: "Kaydet", exact: true }).click();
    await expect(page.getByText("Kaydedildi.")).toBeVisible();

    // The published page is untouched: still the frozen snapshot.
    await expectPublicPageNotToShow(page, kit.slug, "IKINCI BASLIK");
    await expect(page.locator("body")).toContainText("ILK BASLIK");

    // Publishing is what moves it.
    await signIn(page, account);
    await page.goto("/dashboard");
    await page.getByRole("button", { name: "Yayinla" }).first().click();
    await expect(page.getByText("Yayinlandi.")).toBeVisible();

    await expectPublicPageToShow(page, kit.slug, "IKINCI BASLIK");
  });

  /**
   * Sensitive content must never reach the edge. A protected kit answers with
   * the gate only, and the password is checked against the frozen snapshot.
   */
  test("a protected kit hides its content until the password is right", async ({
    page,
    request,
  }) => {
    const account = await registerAccount(request, "locked");
    const kit = await createKit(request, account, "Gizli Kit");
    await addStat(request, account, kit.id, { platform: "YOUTUBE", followers: 91000 });

    const setPassword = await request.put(`${BACKEND}/api/mediakits/${kit.id}/password`, {
      headers: { Authorization: `Bearer ${account.token}` },
      data: { password: "acik-sesame" },
    });
    expect(setPassword.status()).toBe(204);
    await publish(request, account, kit.id);

    await page.goto(`/${kit.slug}`);
    await expect(page.getByText("Bu medya kiti sifre korumali.")).toBeVisible();
    // The gate is all a visitor gets: the numbers are not in the page at all.
    await expect(page.locator("body")).not.toContainText("91");

    await page.getByLabel("Sifre").fill("yanlis-sifre");
    await page.getByRole("button", { name: "Goruntule" }).click();
    await expect(page.getByText("Sifre yanlis.")).toBeVisible();
    await expect(page.locator("body")).not.toContainText("Gizli Kit iÃ§erik");

    await page.getByLabel("Sifre").fill("acik-sesame");
    await page.getByRole("button", { name: "Goruntule" }).click();

    // Now the content is revealed, in the same card the public page uses.
    await expect(page.getByText("Platformlar", { exact: false })).toBeVisible();
    await expect(page.getByText("YouTube")).toBeVisible();
  });
});
