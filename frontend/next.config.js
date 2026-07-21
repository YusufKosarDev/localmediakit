/** @type {import('next').NextConfig} */

// Backend origin the browser is allowed to call (beacon, unlock, dashboard).
const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

// Static CSP (no per-request nonce) — this keeps the public [slug] page
// force-static and edge-cacheable. 'unsafe-inline' on script/style is the
// pragmatic trade-off for Next's inline hydration without a nonce; combined
// with React's auto-escaping it still blocks external script injection.
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
];

const nextConfig = {
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

module.exports = nextConfig;
