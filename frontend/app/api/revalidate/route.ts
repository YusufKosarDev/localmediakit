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

  revalidateTag(`kit-${slug}`);
  return NextResponse.json({ revalidated: true, slug, now: Date.now() });
}
