/*
 * LocalMediaKit service worker.
 *
 * THE RULE THIS FILE EXISTS TO ENFORCE
 * ------------------------------------
 * Published media kits at /<slug> are immutable snapshots served from the edge
 * and revalidated on publish. A service worker sits in front of that entire
 * mechanism: anything it caches, it serves from the browser, and no amount of
 * edge revalidation can dislodge it. A brand could then be looking at a kit
 * the creator replaced days ago.
 *
 * So this worker treats caching as opt-in, never opt-out. It responds only for
 * URLs it explicitly recognises (build assets and a fixed list of signed-in
 * app routes). Everything else — public kit pages, API calls, social images,
 * the backend origin — is left entirely alone: no respondWith, no cache read,
 * no cache write. The browser handles those exactly as if this file did not
 * exist.
 *
 * A deny-list would have been the fragile choice: the next public route added
 * to the app would silently start being cached. With an allow-list, a new
 * route is ignored by default and someone has to deliberately opt it in.
 *
 * Registration is also deliberate — see registerServiceWorker() in the app.
 * Only the signed-in surfaces register this worker, so a visitor who only ever
 * opens a public kit page never installs a service worker at all.
 */

const VERSION = "v1";
const SHELL_CACHE = `lmk-shell-${VERSION}`;
const ASSET_CACHE = `lmk-assets-${VERSION}`;
const OFFLINE_URL = "/offline";

/**
 * Signed-in app routes. Exact matches only — no prefix matching, because a
 * prefix rule like "starts with /d" would be one typo away from swallowing a
 * kit whose slug begins the same way.
 */
const APP_ROUTES = ["/dashboard", "/dashboard/settings", "/login", "/register", OFFLINE_URL];

/** Immutable, content-hashed build output. Safe to serve from cache forever. */
function isBuildAsset(url) {
  return url.pathname.startsWith("/_next/static/");
}

function isAppRoute(url) {
  return APP_ROUTES.includes(url.pathname);
}

/**
 * The single decision this worker makes. Anything that is not an explicit
 * "yes" here is passed through untouched.
 *
 * @returns {"asset"|"app"|null} how to handle the request, or null to ignore
 */
function routeFor(request, scopeOrigin) {
  if (request.method !== "GET") return null;

  let url;
  try {
    url = new URL(request.url);
  } catch {
    return null;
  }

  // Cross-origin (the backend API, remote avatars) is never our business.
  if (url.origin !== scopeOrigin) return null;

  // Never touch the API surface, including the analytics beacon.
  if (url.pathname.startsWith("/api/")) return null;

  if (isBuildAsset(url)) return "asset";
  if (isAppRoute(url)) return "app";

  // Everything left is a public kit page, a social image, or something we
  // have not heard of. All of them go straight to the network.
  return null;
}

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE).then((cache) => cache.addAll([OFFLINE_URL])).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith("lmk-") && key !== SHELL_CACHE && key !== ASSET_CACHE)
            .map((key) => caches.delete(key))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const route = routeFor(event.request, self.location.origin);
  if (!route) return; // pass through — the browser does its normal thing

  if (route === "asset") {
    event.respondWith(cacheFirst(event.request));
    return;
  }
  event.respondWith(networkFirst(event.request));
});

/** Hashed filenames change on every build, so a hit is always correct. */
async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) {
    const cache = await caches.open(ASSET_CACHE);
    cache.put(request, response.clone());
  }
  return response;
}

/**
 * App routes are always fetched fresh when there is a connection; the cache is
 * a fallback for a dropped one, never a shortcut.
 */
async function networkFirst(request) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(SHELL_CACHE);
      cache.put(request, response.clone());
    }
    return response;
  } catch {
    const cached = await caches.match(request);
    if (cached) return cached;
    const offline = await caches.match(OFFLINE_URL);
    if (offline) return offline;
    throw new Error("offline and nothing cached");
  }
}

// Exposed so the test suite can assert the routing contract against the file
// that actually ships, rather than against a copy of its rules.
self.__swRouting = { routeFor, isAppRoute, isBuildAsset, APP_ROUTES };
