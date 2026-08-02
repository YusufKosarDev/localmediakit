import { expect, type Page, type APIRequestContext } from "@playwright/test";

/** Matches playwright.config.ts; see the note there about the default. */
export const BACKEND = process.env.E2E_BACKEND_URL ?? "http://localhost:8080";

/**
 * A throwaway account per test.
 *
 * <p>Isolation is what makes `fullyParallel` safe: two workers running at the
 * same moment never touch the same rows. The `@test.dev` suffix is not
 * decorative — the backend purges accounts with it on startup, so the
 * convention still holds if anyone ever points these tests at a database that
 * outlives the run.
 */
export function uniqueEmail(prefix: string): string {
  const stamp = Date.now().toString(36);
  const noise = Math.random().toString(36).slice(2, 8);
  return `e2e-${prefix}-${stamp}-${noise}@test.dev`;
}

export const PASSWORD = "e2e-password-1234";

export type Account = { email: string; token: string };

/** Registers through the API: signing up is not what most of these tests are about. */
export async function registerAccount(
  request: APIRequestContext,
  prefix: string,
  displayName = "E2E Uretici"
): Promise<Account> {
  const email = uniqueEmail(prefix);
  const response = await request.post(`${BACKEND}/api/auth/register`, {
    data: { email, password: PASSWORD, displayName },
  });
  expect(response.ok(), `register ${email}`).toBeTruthy();
  return { email, token: (await response.json()).token };
}

/**
 * Puts the session token where the app expects it, before any app code runs.
 *
 * <p>addInitScript rather than a post-load evaluate: the dashboard reads the
 * token during its first effect, so setting it afterwards would race the
 * redirect to /login.
 */
export async function signIn(page: Page, account: Account): Promise<void> {
  await page.addInitScript((token) => {
    window.localStorage.setItem("token", token);
  }, account.token);
}

/** Suppresses the welcome tour for tests that are not about onboarding. */
export async function skipOnboarding(request: APIRequestContext, account: Account): Promise<void> {
  const response = await request.post(`${BACKEND}/api/me/onboarding/dismiss`, {
    headers: { Authorization: `Bearer ${account.token}` },
  });
  expect(response.status()).toBe(204);
}

export type Kit = { id: number; slug: string };

export async function createKit(
  request: APIRequestContext,
  account: Account,
  title: string,
  extra: Record<string, unknown> = {}
): Promise<Kit> {
  const slug = uniqueEmail("kit").split("@")[0];
  const response = await request.post(`${BACKEND}/api/mediakits`, {
    headers: { Authorization: `Bearer ${account.token}` },
    data: { title, slug, ...extra },
  });
  expect(response.status(), "create kit").toBe(201);
  const body = await response.json();
  return { id: body.id, slug: body.slug };
}

export async function publish(request: APIRequestContext, account: Account, kitId: number) {
  const response = await request.post(`${BACKEND}/api/mediakits/${kitId}/publish`, {
    headers: { Authorization: `Bearer ${account.token}` },
  });
  expect(response.ok(), "publish").toBeTruthy();
}

export async function addStat(
  request: APIRequestContext,
  account: Account,
  kitId: number,
  stat: Record<string, unknown>
) {
  const response = await request.post(`${BACKEND}/api/mediakits/${kitId}/stats`, {
    headers: { Authorization: `Bearer ${account.token}` },
    data: stat,
  });
  expect(response.status(), "add stat").toBe(201);
}

/**
 * Waits for the published page to serve a given piece of content.
 *
 * <p>Publishing revalidates the static page server-to-server, so there is a
 * short window where the old render is still cached. Polling the page instead
 * of sleeping keeps the test both fast and free of arbitrary waits — and it
 * still fails if the revalidation never happens.
 *
 * <p>Compared case-insensitively: section headings are uppercased by CSS, and
 * innerText returns the transformed text. A presentation detail should not
 * decide whether a content assertion passes.
 */
export async function expectPublicPageToShow(page: Page, slug: string, text: string) {
  await expect
    .poll(
      async () => {
        await page.goto(`/${slug}`, { waitUntil: "domcontentloaded" });
        return (await page.locator("body").innerText()).toLowerCase();
      },
      { timeout: 20_000, message: `public page /${slug} never showed "${text}"` }
    )
    .toContain(text.toLowerCase());
}

/** The counterpart: proves the page is NOT showing something, and stays that way. */
export async function expectPublicPageNotToShow(page: Page, slug: string, text: string) {
  await page.goto(`/${slug}`, { waitUntil: "domcontentloaded" });
  await expect(page.locator("body")).not.toContainText(text, { ignoreCase: true });
}
