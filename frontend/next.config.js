/** @type {import('next').NextConfig} */

// Backend origin the browser is allowed to call (beacon, unlock, dashboard).
const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

// Static CSP (no per-request nonce) — this keeps the public [slug] page
// force-static and edge-cacheable. 'unsafe-inline' on script/style is the
// pragmatic trade-off for Next's inline hydration without a nonce; combined
// with React's auto-escaping it still blocks external script injection.
//
// WHY THERE IS STILL NO NONCE, having looked at it properly.
//
// A nonce is a per-request value by definition, so it needs a proxy generating
// one and rewriting this header, and every route it covers stops being static.
// The public kit page cannot pay that: serving it from the edge without
// touching the backend is the whole architecture. The dashboard routes could,
// since their HTML is a shell that fetches everything client-side -- but the
// only way to exempt the public pages is a matcher, and a mistake in a matcher
// silently makes them dynamic. Trading a certain risk to the product's central
// guarantee for a partial hardening of a surface that was never the problem is
// the wrong way round: the one XSS this project actually had was on the public
// page, which is exactly the route a nonce cannot reach. Escaping the sink
// fixed that (see app/[slug]/json-ld.ts); a nonce would not have.
//
// The directives that do not need a nonce are kept as tight as they can be,
// which is where the value actually is.
const csp = [
  "default-src 'self'",
  "base-uri 'self'",
  "object-src 'none'",
  "frame-ancestors 'none'",
  "form-action 'self'",
  `connect-src 'self' ${BACKEND}`,
  "img-src 'self' data: https:",
  "style-src 'self' 'unsafe-inline'",
  "script-src 'self' 'unsafe-inline'",
  "font-src 'self'",
  // The service worker is same-origin and must stay that way; spelled out
  // rather than left to the script-src fallback so it cannot widen by accident.
  "worker-src 'self'",
  "manifest-src 'self'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: csp },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=(), browsing-topics=()",
  },
  {
    // The host sets this on its own domains, which is exactly why it should be
    // here: a kit link is something a creator sends to a brand, and the first
    // request to a pasted link is the one where a downgrade is possible. Stated
    // in the repository, it survives moving to a custom domain -- which is a
    // planned feature, and the moment the platform stops supplying it.
    key: "Strict-Transport-Security",
    value: "max-age=63072000; includeSubDomains; preload",
  },
  {
    // Nothing here is opened by, or opens, another origin. Isolating the
    // browsing context costs nothing and removes a class of cross-window
    // attacks outright.
    key: "Cross-Origin-Opener-Policy",
    value: "same-origin",
  },
];

const nextConfig = {
  async headers() {
    return [
      { source: "/:path*", headers: securityHeaders },
      {
        // A stale worker is a worker that keeps enforcing yesterday's rules,
        // so it must always be revalidated before use.
        source: "/sw.js",
        headers: [
          { key: "Cache-Control", value: "public, max-age=0, must-revalidate" },
          { key: "Service-Worker-Allowed", value: "/" },
        ],
      },
    ];
  },
};

module.exports = nextConfig;
