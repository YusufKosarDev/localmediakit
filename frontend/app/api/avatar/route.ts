import { put } from "@vercel/blob";
import { NextRequest, NextResponse } from "next/server";

/**
 * Uploads an avatar and returns the URL to store on the account.
 *
 * <p><b>Why this route exists rather than a backend endpoint.</b> The published
 * kit page is static HTML served from the edge and must never depend on the
 * backend being awake — that is the decision the whole project is built on. An
 * avatar served by the Spring instance would put an image behind a service that
 * sleeps after fifteen minutes, so the one thing a brand sees first would be a
 * broken image on a page that otherwise loads instantly. The avatar has to come
 * from a CDN, and this is the side of the system that has one.
 *
 * <p><b>What the account stores is still just a URL.</b> Nothing in the backend
 * changes: it takes an https URL, freezes it into the snapshot at publish, and
 * knows nothing about where it came from. This route only removes the step
 * where the creator had to go and find a URL somewhere else.
 *
 * <p><b>Authorisation is delegated, because this side has no idea who anyone
 * is.</b> The frontend holds no session state; the JWT is the backend's to
 * validate. So the caller's token is presented to /api/me and the upload
 * happens only if the backend recognises it. Skipping that would make this an
 * open file host on somebody else's storage bill.
 */

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

/**
 * Small on purpose. An avatar renders at 40px in the dashboard and 96px on the
 * published page; anything above this is a photo somebody dragged in without
 * looking, and the cost of accepting it is paid on every page view.
 */
const MAX_BYTES = 2 * 1024 * 1024;

/**
 * An allowlist rather than a check for "image/*". The content type is supplied
 * by the client and is not evidence of anything, but it does decide what the
 * blob is later served as -- and "whatever the uploader said" is how a stored
 * file ends up served as HTML on a domain that has a session cookie.
 */
const ALLOWED = new Map([
  ["image/png", "png"],
  ["image/jpeg", "jpg"],
  ["image/webp", "webp"],
  ["image/gif", "gif"],
]);

export async function POST(req: NextRequest) {
  if (!process.env.BLOB_READ_WRITE_TOKEN) {
    // Graceful-enable, the same shape the mail and YouTube integrations use:
    // with no store configured the feature is dark and the URL field is still
    // there. The client hides the control when this answers 503.
    return NextResponse.json({ error: "uploads are not configured" }, { status: 503 });
  }

  const authorization = req.headers.get("authorization");
  if (!authorization) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }

  const who = await fetch(`${BACKEND}/api/me`, {
    headers: { Authorization: authorization },
    cache: "no-store",
  }).catch(() => null);

  if (!who?.ok) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }
  const { id } = (await who.json()) as { id: number };

  const form = await req.formData().catch(() => null);
  const file = form?.get("file");
  // A FormData entry is a string or a file-like, and which File class the
  // second one is depends on the runtime parsing the body -- undici's in a
  // Node server, the DOM's under a test environment. An instanceof check
  // against whichever one happens to be in scope here is a check that passes
  // in production and fails in tests, or the other way round. What the code
  // below actually needs is a type, a size and something to upload.
  if (!file || typeof file === "string") {
    return NextResponse.json({ error: "file is required" }, { status: 400 });
  }

  const extension = ALLOWED.get(file.type);
  if (!extension) {
    return NextResponse.json({ error: "unsupported image type" }, { status: 415 });
  }
  if (file.size > MAX_BYTES) {
    return NextResponse.json({ error: "image is too large" }, { status: 413 });
  }

  // The account id scopes the path and a random suffix finishes it: a
  // predictable name would let one upload overwrite another account's avatar
  // if the id were ever wrong, and would leave a stale CDN entry pointing at
  // the previous image.
  const blob = await put(`avatars/${id}.${extension}`, file, {
    access: "public",
    addRandomSuffix: true,
    contentType: file.type,
  });

  return NextResponse.json({ url: blob.url });
}
