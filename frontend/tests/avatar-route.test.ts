import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

/**
 * The upload route's guards.
 *
 * <p>It writes to somebody else's storage bill on behalf of a session it did
 * not issue, which makes it the one route on this side of the system where
 * getting the checks wrong is expensive rather than merely wrong. The
 * happy-path upload is not tested here — that would be testing Vercel's
 * client — but every reason to refuse one is.
 */
const put = vi.hoisted(() => vi.fn());
vi.mock("@vercel/blob", () => ({ put }));

async function route() {
  return (await import("@/app/api/avatar/route")).POST;
}

/**
 * The two things the route reads, and nothing else.
 *
 * <p>A real Request would be more faithful in principle and less faithful in
 * practice: this suite runs under jsdom, whose Request does not parse a
 * multipart body, so the route would see no file and every case would collapse
 * to the same 400. Supplying formData() directly keeps the test about the
 * route's decisions rather than about which fetch implementation is in scope.
 */
function upload(file: File | null, authorization?: string) {
  const form = new FormData();
  if (file) form.append("file", file);
  return {
    headers: new Headers(authorization ? { authorization } : {}),
    formData: async () => form,
  } as never;
}

const png = (bytes = 10) =>
  new File([new Uint8Array(bytes)], "a.png", { type: "image/png" });

describe("POST /api/avatar", () => {
  beforeEach(() => {
    vi.resetModules();
    put.mockReset();
    put.mockResolvedValue({ url: "https://blob.example/avatars/1-abc.png" });
    process.env.BLOB_READ_WRITE_TOKEN = "test-token";
    process.env.BACKEND_URL = "http://backend";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.BLOB_READ_WRITE_TOKEN;
  });

  function backendSays(status: number, body: unknown = { id: 1 }) {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: status < 400, status, json: async () => body })
    );
  }

  /**
   * Graceful-enable, the shape the mail and YouTube integrations use: with no
   * store configured the feature is dark rather than broken, and the client
   * withdraws the control when it sees this.
   */
  it("reports itself unavailable when no blob store is configured", async () => {
    delete process.env.BLOB_READ_WRITE_TOKEN;
    const res = await (await route())(upload(png(), "Bearer t"));

    expect(res.status).toBe(503);
    expect(put).not.toHaveBeenCalled();
  });

  it("refuses a request with no session", async () => {
    backendSays(200);
    const res = await (await route())(upload(png()));

    expect(res.status).toBe(401);
    expect(put).not.toHaveBeenCalled();
  });

  /**
   * This side of the system holds no session state, so the only way to know
   * whether a token is real is to ask the backend. Skipping that would make
   * this an open file host.
   */
  it("refuses a session the backend does not recognise", async () => {
    backendSays(401, {});
    const res = await (await route())(upload(png(), "Bearer stale"));

    expect(res.status).toBe(401);
    expect(put).not.toHaveBeenCalled();
  });

  it("refuses a type that is not on the allowlist", async () => {
    backendSays(200);
    const svg = new File(["<svg/>"], "x.svg", { type: "image/svg+xml" });
    const res = await (await route())(upload(svg, "Bearer t"));

    // SVG is script, and the stored content type is what it would later be
    // served as.
    expect(res.status).toBe(415);
    expect(put).not.toHaveBeenCalled();
  });

  it("refuses an image past the size limit", async () => {
    backendSays(200);
    const res = await (await route())(upload(png(3 * 1024 * 1024), "Bearer t"));

    expect(res.status).toBe(413);
    expect(put).not.toHaveBeenCalled();
  });

  it("refuses a request with no file", async () => {
    backendSays(200);
    const res = await (await route())(upload(null, "Bearer t"));

    expect(res.status).toBe(400);
    expect(put).not.toHaveBeenCalled();
  });

  it("stores an accepted image under the account and returns its url", async () => {
    backendSays(200, { id: 42 });
    const res = await (await route())(upload(png(), "Bearer t"));

    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ url: "https://blob.example/avatars/1-abc.png" });

    const [pathname, , options] = put.mock.calls[0];
    // Scoped by account, and randomised: a predictable name would let one
    // upload overwrite another account's avatar and would leave a stale CDN
    // entry pointing at the previous image.
    expect(pathname).toBe("avatars/42.png");
    expect(options).toMatchObject({ access: "public", addRandomSuffix: true });
  });
});
