import { describe, it, expect } from "vitest";
import nextConfig from "../next.config.js";

/**
 * The response headers, read from the config that actually ships.
 *
 * <p>Headers are the kind of thing that gets weakened by accident: someone adds
 * an embed, hits a CSP error, widens a directive to unblock themselves, and
 * nothing anywhere disagrees. These assertions are the disagreement. They are
 * deliberately about the directives whose loss would matter rather than the
 * exact string, so tightening the policy does not fail them and loosening it
 * does.
 */
describe("security headers", () => {
  async function headersFor(path: string) {
    const rules = await nextConfig.headers!();
    const rule = rules.find((r: { source: string }) => r.source === path);
    if (!rule) throw new Error(`no header rule for ${path}`);
    return Object.fromEntries(
      rule.headers.map((h: { key: string; value: string }) => [h.key, h.value])
    ) as Record<string, string>;
  }

  it("applies to every route, not just the pages someone remembered", async () => {
    const headers = await headersFor("/:path*");

    expect(Object.keys(headers)).toEqual(
      expect.arrayContaining([
        "Content-Security-Policy",
        "X-Frame-Options",
        "X-Content-Type-Options",
        "Referrer-Policy",
        "Permissions-Policy",
        "Strict-Transport-Security",
        "Cross-Origin-Opener-Policy",
      ])
    );
  });

  it("keeps the CSP directives that hold without a nonce", async () => {
    const csp = (await headersFor("/:path*"))["Content-Security-Policy"];

    // These four do not depend on a nonce, so there is no excuse for losing
    // them: no plugins, no framing, no base-tag rewrite, no form posting to
    // somebody else's server.
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("base-uri 'self'");
    expect(csp).toContain("form-action 'self'");
  });

  it("keeps the service worker same-origin explicitly", async () => {
    const csp = (await headersFor("/:path*"))["Content-Security-Policy"];

    // Spelled out rather than left to the script-src fallback: a worker from
    // another origin would sit in front of every published page this browser
    // ever loads.
    expect(csp).toContain("worker-src 'self'");
  });

  it("does not let script-src reach another origin", async () => {
    const csp = (await headersFor("/:path*"))["Content-Security-Policy"];
    const scriptSrc = csp.split(";").map((d) => d.trim()).find((d) => d.startsWith("script-src"));

    // 'unsafe-inline' is a documented trade-off for staying static. A remote
    // host in this directive would not be a trade-off, it would be the hole
    // the rest of the policy exists to close.
    expect(scriptSrc).toBeDefined();
    expect(scriptSrc).not.toMatch(/https?:\/\//);
  });

  it("asks browsers to remember HTTPS for a long time", async () => {
    const hsts = (await headersFor("/:path*"))["Strict-Transport-Security"];
    const maxAge = Number(/max-age=(\d+)/.exec(hsts)?.[1]);

    // A short max-age is close to no HSTS at all: the protection lapses before
    // the next time most people open the link they were sent.
    expect(maxAge).toBeGreaterThanOrEqual(31536000);
    expect(hsts).toContain("includeSubDomains");
  });

  it("keeps the service worker from being cached into staleness", async () => {
    const headers = await headersFor("/sw.js");

    // A stale worker keeps enforcing yesterday's rules, and it is the one file
    // that can outlive a deploy in someone's browser.
    expect(headers["Cache-Control"]).toContain("max-age=0");
    expect(headers["Cache-Control"]).toContain("must-revalidate");
  });
});
