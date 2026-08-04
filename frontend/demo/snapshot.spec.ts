import { test, expect, type APIRequestContext } from "@playwright/test";
import { caption } from "./caption";

/**
 * The architectural claim, on camera: a published page is a frozen snapshot.
 *
 * <p>The other recording shows what the product does. This one shows the
 * decision the whole design rests on, and it is the harder of the two to
 * demonstrate — the interesting moment is a page that does NOT change. Editing
 * a draft and watching the live page stay exactly as it was is unremarkable
 * unless you know it is the point, which is what the captions are for.
 *
 * <p>Setup happens over the API rather than through the interface. None of it
 * is what this recording is about, and twenty seconds of form filling before
 * the demonstration starts is twenty seconds nobody watches.
 */

const BACKEND = "http://localhost:8080";
const PASSWORD = "demo-parola-1234";

const BEFORE = "Seyahat ve yasam tarzi icerik ureticisi";
const AFTER = "TASLAKTA DEGISTIRILDI";

async function beat(page: { waitForTimeout(ms: number): Promise<void> }, ms = 1000) {
  await page.waitForTimeout(ms);
}

async function seedPublishedKit(request: APIRequestContext) {
  const email = `snapshot-${Date.now().toString(36)}@test.dev`;
  await request.post(`${BACKEND}/api/auth/register`, {
    data: { email, password: PASSWORD, displayName: "Deniz Yilmaz" },
  });
  const token = (await request
    .post(`${BACKEND}/api/auth/login`, { data: { email, password: PASSWORD } })
    .then((r) => r.json())).token as string;
  const auth = { Authorization: `Bearer ${token}` };

  // A title of its own. The other recording's kit uses the same slug space, and
  // a slug freed by one run then reused by the next serves the previous
  // occupant's cached page -- which is a convincing way to record the opposite
  // of what this clip claims.
  const kit = await request
    .post(`${BACKEND}/api/mediakits`, {
      headers: auth,
      data: { title: "Deniz Yilmaz — Moda & Icerik", headline: BEFORE },
    })
    .then((r) => r.json());

  await request.post(`${BACKEND}/api/mediakits/${kit.id}/stats`, {
    headers: auth,
    data: { platform: "INSTAGRAM", followers: 86200, engagementRate: 4.8 },
  });
  await request.post(`${BACKEND}/api/mediakits/${kit.id}/publish`, { headers: auth });

  return { token, auth, kitId: kit.id as number, slug: kit.slug as string, email };
}

test("immutable snapshot", async ({ page, request }) => {
  const { token, auth, kitId, slug } = await seedPublishedKit(request);

  // 1. The published page, as a brand receives it. Waiting for the first
  // regeneration rather than assuming it: the seed publishes moments earlier,
  // and the page is generated on demand, not per request.
  await page.goto(`/${slug}`);
  await expect(async () => {
    await page.reload();
    await expect(page.getByText(BEFORE)).toBeVisible({ timeout: 2000 });
  }).toPass({ timeout: 30_000 });
  await caption(page, "Published page — this is what the brand opens");
  await beat(page, 2200);

  // 2. Change the draft, visibly.
  await page.goto("/login");
  await page.evaluate((t) => window.localStorage.setItem("token", t), token);
  await page.goto("/dashboard");
  await caption(page, "Back in the dashboard: edit the draft");
  await beat(page, 1400);
  await page.getByRole("button", { name: "Duzenle", exact: true }).first().click();
  await beat(page, 800);

  const headline = page.locator(`#kit-headline-${kitId}`);
  await headline.click();
  await headline.fill("");
  await headline.pressSequentially(AFTER, { delay: 60 });
  await beat(page, 700);
  await page.getByRole("button", { name: "Kaydet", exact: true }).first().click();
  // Assert the save landed. Without this the recording can sail past a click
  // that did nothing and go on to "prove" immutability with an unchanged
  // draft -- the one failure that would make this clip a lie.
  await expect(page.getByText("Kaydedildi.")).toBeVisible();
  await caption(page, "Draft saved");
  await beat(page, 1600);

  // 3. The claim. The draft moved; the published page did not.
  await page.goto(`/${slug}`);
  await expect(page.getByText(BEFORE)).toBeVisible();
  await expect(page.getByText(AFTER)).toHaveCount(0);
  await caption(page, "Public page is unchanged — it serves a frozen snapshot");
  await beat(page, 2600);

  // 4. And it moves only when the creator says so.
  await page.goto("/dashboard");
  await caption(page, "Publish again");
  await beat(page, 1200);
  await page.getByRole("button", { name: "Yayinla" }).first().click();
  await expect(page.getByText("Yayinlandi.")).toBeVisible();
  await beat(page, 1400);

  // Publish triggers an on-demand revalidation, and the regenerated page lands
  // a moment later. Reloading until it does is what a person would do, and it
  // keeps the clip honest about the page being regenerated rather than
  // re-rendered per request.
  await page.goto(`/${slug}`);
  await expect(async () => {
    await page.reload();
    await expect(page.getByText(AFTER)).toBeVisible({ timeout: 2000 });
  }).toPass({ timeout: 30_000 });
  await caption(page, "Now it changes — publish is the only thing that moves it");
  await beat(page, 2600);

  await request
    .delete(`${BACKEND}/api/me`, {
      headers: auth,
      data: { currentPassword: PASSWORD, confirmation: "HESABIMI SIL" },
    })
    .catch(() => undefined);
});
