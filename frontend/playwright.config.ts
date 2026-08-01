import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against the real stack: a real browser, the real Next.js
 * production build, and the real Spring backend.
 *
 * <p><b>Why local rather than production.</b> The deployed backend sleeps on a
 * free tier and takes about a minute to wake, which would make every run
 * flaky-by-construction. More importantly these tests publish: run against
 * production and they would create real public pages, real edge-cache entries
 * and real analytics rows on the live site. Locally the backend runs on an
 * in-memory H2 with no external dependencies at all — no Docker, no Postgres,
 * no secrets — and the whole dataset disappears with the process, so isolation
 * and cleanup are free.
 *
 * <p><b>Why a production build and not `next dev`.</b> The public page is
 * force-static and only changes when a publish triggers revalidateTag. In dev
 * that caching does not exist, so the snapshot test would pass without
 * exercising the mechanism it claims to prove.
 */
const BACKEND_URL = "http://localhost:8080";
const FRONTEND_URL = "http://localhost:3000";

/** Matches the backend's own default, so nothing has to be configured. */
const REVALIDATE_SECRET = "local-dev-secret";

export default defineConfig({
  testDir: "./e2e",
  // These drive a browser through multi-step flows including a publish and an
  // edge revalidation; the component suite's defaults are far too tight.
  timeout: 60_000,
  expect: { timeout: 15_000 },

  // Every test creates its own account, so parallel workers cannot collide.
  fullyParallel: true,
  // A retry masks flakiness rather than fixing it. If one of these fails it is
  // supposed to be read, not re-rolled.
  retries: 0,
  forbidOnly: !!process.env.CI,
  workers: process.env.CI ? 2 : undefined,

  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["list"]],

  use: {
    baseURL: FRONTEND_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },

  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],

  // Playwright starts both processes and waits for each to answer before the
  // first test runs, so a slow cold start is a wait rather than a failure.
  webServer: [
    {
      command: "mvn -B -q --no-transfer-progress -f ../backend/pom.xml spring-boot:run",
      url: `${BACKEND_URL}/actuator/health`,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
      stdout: "ignore",
      stderr: "pipe",
      env: { REVALIDATE_URL: `${FRONTEND_URL}/api/revalidate` },
    },
    {
      command: "pnpm run start",
      url: FRONTEND_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      stdout: "ignore",
      stderr: "pipe",
      env: {
        NEXT_PUBLIC_BACKEND_URL: BACKEND_URL,
        REVALIDATE_SECRET,
      },
    },
  ],
});
