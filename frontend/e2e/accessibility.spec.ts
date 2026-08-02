import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { createKit, publish, registerAccount, signIn, skipOnboarding } from "./support";

/**
 * An automated accessibility pass over the surfaces that matter.
 *
 * <p>The palette test already proves the colours are readable, which is the
 * part most likely to be got wrong by hand. It cannot see anything else: a
 * control with no accessible name, a heading level skipped, a form field with
 * no label, a landmark missing. Those are the failures that make a page
 * unusable with a screen reader while looking perfectly fine.
 *
 * <p>This matters more here than on a typical dashboard. The public kit page is
 * a link a creator sends to someone they want to work with, opened by a person
 * whose setup they cannot know anything about. It is also the page they never
 * see themselves after publishing.
 *
 * <p>Scoped to WCAG A and AA. Automated rules catch a minority of real
 * accessibility problems and this is not a substitute for using the thing with
 * a keyboard -- but everything it does catch is unambiguous, which makes it
 * worth failing a build over.
 */
const WCAG = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

test.describe("accessibility", () => {
  /**
   * Audited with reduced motion, for a reason worth stating.
   *
   * <p>The public page fades its sections in on a stagger, and axe measures
   * whatever colour is on screen at the instant it runs. Mid-fade that is a
   * blend toward the background -- the first run reported six contrast
   * failures at a ratio of 1.42 against a foreground colour that appears
   * nowhere in the stylesheet. None of it was real.
   *
   * <p>Waiting for the animation would make the audit depend on a timer, which
   * is how a suite acquires a flaky test. Reduced motion removes the animation
   * instead of racing it, and it is a real setting real people have -- so this
   * also covers a rendering path nothing else did.
   */
  test.beforeEach(async ({ page }) => {
    // Before any navigation, so the stylesheet sees it on first paint.
    await page.emulateMedia({ reducedMotion: "reduce" });
  });

  test("the public kit page has no violations", async ({ page, request }) => {
    const account = await registerAccount(request, "a11y-public");
    const kit = await createKit(request, account, "Erisilebilirlik Kiti");
    await publish(request, account, kit.id);

    await page.goto(`/${kit.slug}`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(WCAG).analyze();

    expect(describe(results.violations)).toEqual([]);
  });

  test("sign-in has no violations", async ({ page }) => {
    // The first thing anyone sees, and entirely form controls -- the shape most
    // prone to missing labels.
    await page.goto("/login");
    await expect(page.getByRole("button", { name: /giris|sign in/i }).first()).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(WCAG).analyze();

    expect(describe(results.violations)).toEqual([]);
  });

  test("the dashboard has no violations", async ({ page, request }) => {
    const account = await registerAccount(request, "a11y-dashboard");
    // Without this the welcome tour sits over the page and the audit reports
    // the dialog rather than the dashboard behind it.
    await skipOnboarding(request, account);
    await signIn(page, account);
    await page.goto("/dashboard");
    await expect(page.getByRole("heading", { name: "Yeni medya kiti" })).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(WCAG).analyze();

    expect(describe(results.violations)).toEqual([]);
  });
});

/**
 * Turns axe's output into something a failure message can be read from. The
 * raw objects are enormous, so asserting on them prints pages of JSON and
 * buries the one line that says what is wrong. The measured data is kept
 * because for contrast failures it is the whole answer -- which two colours,
 * and how far off -- and without it the next person starts by guessing.
 */
type Violation = {
  id: string;
  help: string;
  nodes: Array<{ target: unknown[]; any?: Array<{ data?: unknown }> }>;
};

function describe(violations: Violation[]): string[] {
  return violations.map((v) => {
    const node = v.nodes[0];
    const measured = node?.any?.find((check) => check.data)?.data;
    const detail = measured ? ` ${JSON.stringify(measured)}` : "";
    return `${v.id}: ${v.help} (${v.nodes.length} element(s), first: ${String(node?.target)})${detail}`;
  });
}
