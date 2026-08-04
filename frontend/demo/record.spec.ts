import { test, expect, type Page } from "@playwright/test";
import { caption, clearCaption } from "./caption";

/**
 * The README demo, in one take: sign up, build a kit, publish it, and read the
 * result as a brand would.
 *
 * The pauses are the point. A recording that moves at machine speed is
 * unreadable, so each step holds long enough to be followed. That is also why
 * this file lives outside `e2e/` -- arbitrary waits belong in a screencast,
 * never in a test that is supposed to mean something when it fails.
 */

const BACKEND = "http://localhost:8080";
const PASSWORD = "demo-parola-1234";

/** Types at human speed so the recording shows the text appearing. */
async function typeSlowly(page: Page, selector: string, text: string) {
  await page.locator(selector).click();
  await page.locator(selector).pressSequentially(text, { delay: 55 });
}

async function beat(page: Page, ms = 900) {
  await page.waitForTimeout(ms);
}

test("localmediakit demo", async ({ page, request }) => {
  const email = `demo-${Date.now().toString(36)}@test.dev`;

  // 1. Sign up.
  await page.goto("/register");
  await beat(page, 1200);
  await typeSlowly(page, "#name", "Elif Demirtas");
  await typeSlowly(page, "#email", email);
  await typeSlowly(page, "#password", PASSWORD);
  await beat(page, 600);
  await page.getByRole("button", { name: "Kayit ol" }).click();

  // 2. The dashboard opens with the welcome tour; step through it once.
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await beat(page, 1600);
  await dialog.getByRole("button", { name: "Devam" }).click();
  await beat(page, 1800);
  await dialog.getByRole("button", { name: "Atla" }).click();
  await beat(page);

  // 3. Create the kit.
  await typeSlowly(page, "input[placeholder='Baslik *']", "Elif Demirtas — Seyahat & Yasam");
  await beat(page, 500);
  await page.getByRole("button", { name: "Olustur", exact: true }).click();
  await expect(page.getByText("DRAFT").first()).toBeVisible();
  await beat(page, 1200);

  // 4. Add real numbers through the stats panel.
  const panelLoaded = page.waitForResponse(
    (r) => r.url().includes("/api/mediakits/") && r.url().endsWith("/sources")
  );
  await page.getByRole("button", { name: "Istatistik & Kitle" }).click();
  await panelLoaded;
  await expect(page.getByText("Platform istatistikleri")).toBeVisible();
  await beat(page);

  await typeSlowly(page, "input[placeholder='takipci *']", "128400");
  await beat(page, 500);
  await page.getByRole("button", { name: "Olcum ekle" }).click();
  await expect(page.getByText("128.400", { exact: false })).toBeVisible();
  await beat(page, 1400);

  // The rest of the kit is filled in through the API rather than on camera.
  // Every one of these has its own panel in the dashboard, but clicking through
  // all of them would make the recording four times as long to watch and no
  // more informative -- while an almost empty public page at the end would
  // undersell what publish actually freezes.
  const token = (await page.evaluate(() => window.localStorage.getItem("token")))!;
  const auth = { Authorization: `Bearer ${token}` };
  const kitId = Number(
    new URL(page.url()).searchParams.get("kit") ??
      (await request.get(`${BACKEND}/api/mediakits`, { headers: auth }).then(async (r) =>
        (await r.json())[0].id
      ))
  );

  for (const stat of [
    { platform: "INSTAGRAM", followers: 86200, engagementRate: 4.8 },
    { platform: "TIKTOK", followers: 41500, engagementRate: 7.1 },
  ]) {
    await request.post(`${BACKEND}/api/mediakits/${kitId}/stats`, { headers: auth, data: stat });
  }

  await request.put(`${BACKEND}/api/mediakits/${kitId}/demographics`, {
    headers: auth,
    data: {
      entries: [
        { category: "AGE", label: "18-24", percentage: 34 },
        { category: "AGE", label: "25-34", percentage: 41 },
        { category: "AGE", label: "35-44", percentage: 18 },
        { category: "GENDER", label: "Kadin", percentage: 63 },
        { category: "GENDER", label: "Erkek", percentage: 37 },
        { category: "COUNTRY", label: "Turkiye", percentage: 72 },
        { category: "COUNTRY", label: "Almanya", percentage: 11 },
      ],
    },
  });

  for (const collab of [
    { brandName: "Kayak Tourism", campaign: "Kapadokya kis serisi", period: "2026 Q1", resultNote: "3 Reels, 412 B goruntulenme" },
    { brandName: "Nordic Outdoor", campaign: "Ekipman incelemesi", period: "2025 Q4", resultNote: "YouTube uzun form, %6,2 etkilesim" },
  ]) {
    await request.post(`${BACKEND}/api/mediakits/${kitId}/collaborations`, { headers: auth, data: collab });
  }

  for (const item of [
    { serviceName: "Instagram Reels", priceAmount: 18000, currency: "TRY" },
    { serviceName: "YouTube entegrasyon", priceAmount: 45000, currency: "TRY" },
    { serviceName: "Paket (Reels + Story)", priceAmount: 26000, currency: "TRY" },
  ]) {
    await request.post(`${BACKEND}/api/mediakits/${kitId}/ratecard`, { headers: auth, data: item });
  }

  await page.reload();
  await expect(page.getByRole("button", { name: "Yayinla" }).first()).toBeVisible();
  await beat(page, 1200);

  // 5. Publish: the moment the draft becomes an immutable snapshot.
  await caption(page, "Publish — freezes the draft into a snapshot");
  await page.getByRole("button", { name: "Yayinla" }).first().click();
  await expect(page.getByText("Yayinlandi.")).toBeVisible();
  await beat(page, 1800);

  // 6. Read it as a brand would -- served static from the edge in production.
  const link = page.locator('a[href^="/"]:has-text("/")').first();
  const slug = (await link.getAttribute("href"))!.replace("/", "");

  await page.goto(`/${slug}`);
  await expect(page.getByText("Platformlar", { exact: false })).toBeVisible();
  await caption(page, "The link a brand opens — static, served from the edge");
  await beat(page, 1800);
  await page.mouse.wheel(0, 420);
  await beat(page, 1400);
  await page.mouse.wheel(0, 420);
  await beat(page, 2000);
  await clearCaption(page);

  // Keep the run self-contained: the account is a @test.dev one, which the
  // backend purges on startup, but the recording should not depend on that.
  if (token) {
    await request
      .delete(`${BACKEND}/api/me`, {
        headers: { Authorization: `Bearer ${token}` },
        data: { currentPassword: PASSWORD, confirmation: "HESABIMI SIL" },
      })
      .catch(() => undefined);
  }
});
