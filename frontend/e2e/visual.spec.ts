import { test, expect } from "@playwright/test";
import { registerAccount, createKit, addStat, publish } from "./support";

/**
 * Pixel comparison of the surfaces a stranger sees.
 *
 * <p><b>Why this was skipped for so long, and what changed.</b> The objection
 * was real: a baseline carries the font rasterisation of the machine that made
 * it, so one generated on a laptop and compared on a CI runner is red on the
 * first run and every run after, and a suite that is always red is a suite
 * nobody reads. That is an argument against baselines made in the wrong place,
 * not against pixel testing. These baselines are generated on the CI runner
 * and compared on the CI runner, on a pinned OS image, so the machine that
 * made them is the machine that reads them and the ordinary result is a
 * zero-pixel diff.
 *
 * <p><b>What it catches that nothing else does.</b> The rest of the suite
 * checks that text is present, that an element is visible, that contrast ratios
 * pass. All of that stays true when a layout collapses into a single column, a
 * card loses its background, a section overlaps the one below it, or a theme
 * variable resolves to the wrong colour. The published page is this product --
 * a creator sends it to a brand and never sees it again -- so "it still says
 * the right words somewhere on the page" is not the standard it needs to hold
 * to.
 *
 * <p><b>Scope is deliberately narrow.</b> Three shots of the one page whose
 * appearance is the deliverable, in the two themes and at the width most
 * brands will actually open it on. The dashboard is not here: it changes with
 * every feature, its owner is looking right at it, and baselines for it would
 * be churn that trains everyone to update snapshots without reading them --
 * which is how pixel testing turns into a rubber stamp.
 */
test.describe("visual", () => {
  /**
   * Only where the baselines came from.
   *
   * <p>Playwright suffixes a snapshot with the platform that produced it, so a
   * run on any other one finds no baseline, writes the current render as the
   * new truth and reports a failure. Neither half of that is useful: the file
   * it leaves behind is a Windows or macOS rendering that CI will never
   * compare against, and the failure says nothing about the page.
   *
   * <p>Skipping is the honest answer rather than a gap. These assertions are
   * about pixels, pixels are a property of the renderer, and the renderer that
   * matters is the one in CI. Everything else in this suite -- the flows, the
   * snapshot guarantee, the axe audit -- still runs everywhere.
   */
  test.skip(
    process.platform !== "linux",
    "visual baselines are generated on the CI runner; see .github/workflows/e2e.yml"
  );

  /**
   * The kit every shot is taken of.
   *
   * <p>Fixed numbers and fixed text on purpose: a rendered date, a relative
   * time or a random follower count would change the pixels every run and the
   * baseline would be measuring the clock.
   */
  async function publishedKit(request: Parameters<typeof registerAccount>[0], label: string) {
    const account = await registerAccount(request, label);
    const kit = await createKit(request, account, "Görsel Regresyon Kiti");
    await addStat(request, account, kit.id, {
      platform: "YOUTUBE",
      followers: 102000,
      avgViews: 44000,
      avgLikes: 3100,
      avgComments: 210,
    });
    await addStat(request, account, kit.id, {
      platform: "INSTAGRAM",
      followers: 64000,
      avgLikes: 3800,
      avgComments: 340,
    });
    await publish(request, account, kit.id);
    return kit;
  }

  test("the published page looks the way it is meant to", async ({ page, request }) => {
    const kit = await publishedKit(request, "visual-light");
    await page.goto(`/${kit.slug}`);

    // The beacon fires after render and changes nothing on screen, but a shot
    // taken mid-flight can catch a half-painted frame.
    await page.waitForLoadState("networkidle");

    await expect(page).toHaveScreenshot("kit-light.png", { fullPage: true });
  });

  test("the dark theme is a theme, not an inversion", async ({ page, request }) => {
    const kit = await publishedKit(request, "visual-dark");
    await page.goto(`/${kit.slug}`);
    await page.waitForLoadState("networkidle");

    // Set on the kit's own wrapper, which is where the published language and
    // theme live -- the root layout is shared and knows nothing about a kit.
    await page.evaluate(() => {
      document.querySelector("[data-theme]")?.setAttribute("data-theme", "dark");
    });

    await expect(page).toHaveScreenshot("kit-dark.png", { fullPage: true });
  });

  test("the page holds together on a phone", async ({ page, request }) => {
    const kit = await publishedKit(request, "visual-mobile");
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`/${kit.slug}`);
    await page.waitForLoadState("networkidle");

    await expect(page).toHaveScreenshot("kit-mobile.png", { fullPage: true });
  });
});
