"use client";

import { useEffect } from "react";

const BACKEND = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

// Fire-and-forget view beacon. The page itself is static and edge-cached;
// this fires AFTER render, never blocks anything, and fails silently when the
// backend is asleep (best-effort analytics; waking the backend is a side
// benefit). keepalive lets the request survive quick tab closes.
export default function TrackView({ slug }: { slug: string }) {
  useEffect(() => {
    try {
      fetch(`${BACKEND}/api/track`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ slug, referrer: document.referrer || null }),
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
