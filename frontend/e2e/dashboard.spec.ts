import { test, expect } from "@playwright/test";
import {
  BACKEND, addStat, createKit, expectPublicPageToShow, publish,
  registerAccount, signIn, skipOnboarding,
} from "./support";

test.describe("dashboard", () => {
  /**
   * The beacon is deliberately fire-and-forget, which is exactly why it needs
   * an end-to-end check: it is posted by the browser after render, and nothing
   * in the page reacts if it fails. Only the owner's analytics tab can tell
   * whether a visit was actually recorded.
   */
  test("a visit to a published page shows up in the owner's analytics", async ({
    page,
    request,
  }) => {
    const account = await registerAccount(request, "beacon");
    await skipOnboarding(request, account);
    const kit = await createKit(request, account, "Analitik Kiti");
    await publish(request, account, kit.id);

    await expectPublicPageToShow(page, kit.slug, "Analitik Kiti");

    // The counter is written asynchronously; poll the owner's own API rather
    // than sleeping, so the test is as fast as the system allows.
    await expect
      .poll(
        async () => {
          const response = await request.get(`${BACKEND}/api/mediakits/${kit.id}/analytics`, {
            headers: { Authorization: `Bearer ${account.token}` },
          });
          return (await response.json()).totalViews as number;
        },
        { timeout: 20_000, message: "the visit never reached the view counter" }
      )
      .toBeGreaterThan(0);

    // And the owner sees it in the interface, not just in the API.
    await signIn(page, account);
    await page.goto("/dashboard");
    await page.getByRole("button", { name: "Analitik" }).click();
    await expect(page.getByText("toplam goruntulenme")).toBeVisible();
  });

  /**
   * Language is an account setting, so switching it has to survive a page
   * load — a component test can only prove the toggle re-renders.
   */
  test("switching the account language redraws the dashboard in English", async ({
    page,
    request,
  }) => {
    const account = await registerAccount(request, "locale");
    await skipOnboarding(request, account);
    await signIn(page, account);

    await page.goto("/dashboard/settings");
    await expect(page.getByRole("heading", { name: "Hesap ayarlari" })).toBeVisible();

    await page.getByLabel("Arayuz dili").selectOption("en");
    await page.getByRole("button", { name: "Save language" }).click();
    await expect(page.getByText("Profile updated.")).toBeVisible();

    // Reload: the choice came back from the account, not from component state.
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: "New media kit" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Settings" })).toBeVisible();
    await expect(page.getByText("Yeni medya kiti")).toHaveCount(0);
  });

  /**
   * The tour is meant to introduce the product once. Showing it again to
   * someone who dismissed it is the failure mode worth guarding, and it can
   * only be seen across a reload.
   */
  test("the welcome tour appears once and stays dismissed", async ({ page, request }) => {
    const account = await registerAccount(request, "tour");
    await signIn(page, account);

    await page.goto("/dashboard");
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();
    // It leads with the publish rule — the one concept the product hides.
    await dialog.getByRole("button", { name: "Devam" }).click();
    await expect(dialog.getByText("En onemlisi: Yayinla")).toBeVisible();

    await dialog.getByRole("button", { name: "Atla" }).click();
    await expect(dialog).toBeHidden();

    // Dismissing the checklist is what records it against the account.
    await page.getByRole("button", { name: "Gizle" }).click();

    await page.reload();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "Baslangic adimlari" })).toHaveCount(0);
  });

  /**
   * A kit carries its own presentation language, separate from the dashboard's.
   * A creator pitching Turkish brands with one kit and international ones with
   * another needs both at the same time — so this checks two live pages in two
   * languages from a single account.
   */
  test("two kits from one account can be published in different languages", async ({
    page,
    request,
  }) => {
    const account = await registerAccount(request, "twolang");
    await skipOnboarding(request, account);

    const turkish = await createKit(request, account, "Turkce Kit", { language: "tr" });
    const english = await createKit(request, account, "English Kit", { language: "en" });
    await addStat(request, account, turkish.id, { platform: "YOUTUBE", followers: 1000 });
    await addStat(request, account, english.id, { platform: "YOUTUBE", followers: 1000 });
    await publish(request, account, turkish.id);
    await publish(request, account, english.id);

    await expectPublicPageToShow(page, turkish.slug, "Platformlar");
    await expect(page.locator("[lang='tr']").first()).toBeVisible();

    await expectPublicPageToShow(page, english.slug, "Platforms");
    await expect(page.locator("[lang='en']").first()).toBeVisible();
  });
});
