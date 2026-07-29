import { describe, it, expect, beforeAll } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import vm from "node:vm";

const ORIGIN = "https://localmediakit.vercel.app";

/** Vitest runs with the frontend package as its working directory. */
function read(relative: string) {
  return readFileSync(resolve(process.cwd(), relative), "utf8");
}

/**
 * Loads the real public/sw.js in a sandbox with a stubbed worker global, so
 * these assertions run against the file that actually ships rather than a
 * restatement of its rules.
 */
function loadServiceWorker() {
  const listeners: Record<string, unknown> = {};
  const self: Record<string, unknown> = {
    location: { origin: ORIGIN },
    addEventListener: (type: string, fn: unknown) => {
      listeners[type] = fn;
    },
    skipWaiting: () => {},
    clients: { claim: () => {} },
  };
  const sandbox = {
    self,
    // URL is a worker global; without it every parse would throw and the
    // router would fall through to "ignore" for everything — which would make
    // the pass-through assertions below pass for the wrong reason.
    URL,
    caches: { open: async () => ({}), keys: async () => [], match: async () => undefined },
    fetch: async () => ({}),
  };
  vm.createContext(sandbox);
  vm.runInContext(read("public/sw.js"), sandbox);
  return {
    routing: self.__swRouting as {
      routeFor: (req: { method: string; url: string }, origin: string) => string | null;
      APP_ROUTES: string[];
    },
    listeners,
  };
}

const get = (url: string) => ({ method: "GET", url });

describe("service worker routing", () => {
  let routeFor: (req: { method: string; url: string }, origin: string) => string | null;

  beforeAll(() => {
    routeFor = loadServiceWorker().routing.routeFor;
  });

  /*
   * The reason this worker is constrained at all: published kits are edge
   * cached snapshots that publishing replaces. Anything the worker caches, it
   * serves from the browser, where no revalidation can reach it.
   */
  it("never handles a public media kit page", () => {
    const publicPages = [
      `${ORIGIN}/demo`,
      `${ORIGIN}/ornek-medya-kiti`,
      `${ORIGIN}/ayse-yilmaz`,
      // A slug that starts like an app route must not be mistaken for one.
      `${ORIGIN}/dashboard-ipuclari`,
      `${ORIGIN}/login-rehberi`,
      // Nested and query-bearing variants.
      `${ORIGIN}/kit/alt-sayfa`,
      `${ORIGIN}/demo?utm_source=brand`,
    ];
    for (const url of publicPages) {
      expect(routeFor(get(url), ORIGIN), url).toBeNull();
    }
  });

  it("never handles the social image of a public kit", () => {
    expect(routeFor(get(`${ORIGIN}/demo/opengraph-image`), ORIGIN)).toBeNull();
  });

  it("never handles API calls, so the analytics beacon is untouched", () => {
    expect(routeFor({ method: "POST", url: `${ORIGIN}/api/track` }, ORIGIN)).toBeNull();
    expect(routeFor(get(`${ORIGIN}/api/revalidate`), ORIGIN)).toBeNull();
  });

  it("never handles the backend origin", () => {
    expect(routeFor(get("https://localmediakit.onrender.com/api/me"), ORIGIN)).toBeNull();
    expect(routeFor(get("https://cdn.example.com/avatar.png"), ORIGIN)).toBeNull();
  });

  it("ignores anything that is not a GET", () => {
    for (const method of ["POST", "PUT", "DELETE", "HEAD"]) {
      expect(routeFor({ method, url: `${ORIGIN}/dashboard` }, ORIGIN)).toBeNull();
    }
  });

  it("handles the signed-in app routes it was given", () => {
    expect(routeFor(get(`${ORIGIN}/dashboard`), ORIGIN)).toBe("app");
    expect(routeFor(get(`${ORIGIN}/dashboard/settings`), ORIGIN)).toBe("app");
    expect(routeFor(get(`${ORIGIN}/login`), ORIGIN)).toBe("app");
    expect(routeFor(get(`${ORIGIN}/offline`), ORIGIN)).toBe("app");
  });

  it("handles content-hashed build assets", () => {
    expect(routeFor(get(`${ORIGIN}/_next/static/chunks/main-abc123.js`), ORIGIN)).toBe("asset");
  });

  it("matches app routes exactly, never by prefix", () => {
    // "/dashboard/" and "/dashboardx" are not the dashboard.
    expect(routeFor(get(`${ORIGIN}/dashboard/`), ORIGIN)).toBeNull();
    expect(routeFor(get(`${ORIGIN}/dashboardx`), ORIGIN)).toBeNull();
  });

  it("keeps the app route list free of anything that could match a slug", () => {
    const { routing } = loadServiceWorker();
    for (const route of routing.APP_ROUTES) {
      expect(route.startsWith("/")).toBe(true);
      // A bare "/" would put every public page in scope.
      expect(route.length).toBeGreaterThan(1);
    }
  });
});

describe("web app manifest", () => {
  const manifest = JSON.parse(read("public/manifest.webmanifest"));

  it("declares what a browser needs before it will offer to install", () => {
    expect(manifest.name).toBeTruthy();
    expect(manifest.short_name).toBeTruthy();
    expect(manifest.start_url).toBe("/dashboard");
    expect(manifest.display).toBe("standalone");
    expect(manifest.theme_color).toBe("#6d40e6");
  });

  it("ships the icon sizes installability requires, plus a maskable one", () => {
    const sizes = manifest.icons.map((i: { sizes: string }) => i.sizes);
    expect(sizes).toContain("192x192");
    expect(sizes).toContain("512x512");
    expect(
      manifest.icons.some((i: { purpose: string }) => i.purpose === "maskable")
    ).toBe(true);
  });

  it("starts on the dashboard, not on a public kit page", () => {
    // Launching into someone's published snapshot would both be the wrong
    // screen and put the installed app on a route the worker must not serve.
    expect(manifest.start_url.startsWith("/dashboard")).toBe(true);
  });
});
