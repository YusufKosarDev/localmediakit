import { revalidateTag } from "next/cache";
import { NextRequest, NextResponse } from "next/server";

// Secret-protected on-demand revalidation endpoint.
// Called server-to-server by the Spring backend after a publish.
export async function POST(req: NextRequest) {
  const secret = req.headers.get("x-revalidate-secret");
  if (!secret || secret !== process.env.REVALIDATE_SECRET) {
    return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  }

  const body = await req.json().catch(() => null);
  const slug = body?.slug;
  if (!slug || typeof slug !== "string") {
    return NextResponse.json({ error: "slug is required" }, { status: 400 });
  }

  // The second argument (Next 16) is the cacheLife profile the tag is expired
  // against; "max" is the documented equivalent of the old single-argument call.
  // Its sibling updateTag() would expire and re-render in the same request, but
  // it is a Server Action API and this is a route handler called server-to-server
  // by the backend — there is no render here to refresh, only a cache entry to
  // drop. The publish flow is unaffected: the creator publishes, then shares the
  // link, so the regeneration happens long before a brand asks for the page.
  revalidateTag(`kit-${slug}`, "max");
  return NextResponse.json({ revalidated: true, slug, now: Date.now() });
}
