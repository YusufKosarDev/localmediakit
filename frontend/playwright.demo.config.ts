import { defineConfig, devices } from "@playwright/test";

/**
 * Records the README demo. Separate from playwright.config.ts on purpose: this
 * is not a test and must never run in CI. It drives the same local stack the
 * end-to-end suite uses, but with video on, one worker and deliberate pacing
 * so the result is watchable.
 *
 * Run with: pnpm run demo:record
 */
const BACKEND_URL = "http://localhost:8080";
const FRONTEND_URL = "http://localhost:3000";
const REVALIDATE_SECRET = "local-dev-secret";

export default defineConfig({
  testDir: "./demo",
  timeout: 180_000,
  expect: { timeout: 20_000 },
  workers: 1,
  retries: 0,
  reporter: [["list"]],

  use: {
    baseURL: FRONTEND_URL,
    viewport: { width: 1280, height: 720 },
    video: { mode: "on", size: { width: 1280, height: 720 } },
  },

  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],

  webServer: [
    {
      command: "mvn -B -q --no-transfer-progress -f ../backend/pom.xml spring-boot:run",
      url: `${BACKEND_URL}/actuator/health`,
      reuseExistingServer: true,
      timeout: 180_000,
      stdout: "ignore",
      stderr: "pipe",
      env: { REVALIDATE_URL: `${FRONTEND_URL}/api/revalidate` },
    },
    {
      command: "pnpm run start",
      url: FRONTEND_URL,
      reuseExistingServer: true,
      timeout: 120_000,
      stdout: "ignore",
      stderr: "pipe",
      env: { NEXT_PUBLIC_BACKEND_URL: BACKEND_URL, REVALIDATE_SECRET },
    },
  ],
});
