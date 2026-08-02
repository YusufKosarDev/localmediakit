"use client";

import { useEffect } from "react";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

// Fire-and-forget view beacon. The page itself is static and edge-cached;
// this fires AFTER render, never blocks anything, and fails silently when the
// backend is asleep (best-effort analytics; waking the backend is a side
// benefit). keepalive lets the request survive quick tab closes.
/** Length cap mirrors the backend's; a longer value cannot be a real token. */
const MAX_SHARE_TOKEN = 32;

/**
 * The ?r= label a creator put on the link they sent. Read here rather than on
 * the server on purpose: the page is force-static and shared by every visitor,
 * so anything per-recipient has to stay out of the cached HTML. The token never
 * touches the rendered page -- it goes straight from the URL into the beacon.
 */
function shareTokenFromUrl(): string | null {
  try {
    const value = new URLSearchParams(window.location.search).get("r");
    if (!value || value.length > MAX_SHARE_TOKEN) return null;
    return value;
  } catch {
    return null;
  }
}

export default function TrackView({ slug }: { slug: string }) {
  useEffect(() => {
    try {
      fetch(`${BACKEND}/api/track`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          slug,
          referrer: document.referrer || null,
          shareToken: shareTokenFromUrl(),
        }),
        keepalive: true,
      }).catch(() => {
        // Best-effort: a sleeping backend must never surface as an error.
      });
    } catch {
      // Same: tracking must never break the page.
    }
  }, [slug]);

  return null;
}
