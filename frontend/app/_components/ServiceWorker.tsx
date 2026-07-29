"use client";

import { useEffect } from "react";

/**
 * Registers the service worker — and does so from the signed-in surfaces only.
 *
 * <p>This component is deliberately NOT mounted in the root layout. Public
 * media-kit pages are what brands look at, and they are edge-cached snapshots
 * that publishing replaces. Keeping registration off those pages means a
 * visitor who only ever opens a kit link never has a service worker installed
 * at all, so there is nothing in their browser that could outlive a publish.
 *
 * <p>The worker itself also refuses to cache those routes (see public/sw.js).
 * Both together: the people most likely to see a stale page never get a
 * worker, and the worker could not serve them one anyway.
 */
export function ServiceWorker() {
  useEffect(() => {
    if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return;
    // Scope has to be the origin: start_url is /dashboard, and a worker served
    // from a subdirectory could not control it (scope "/dashboard/" does not
    // cover "/dashboard"). Breadth of scope is fine — the worker's fetch
    // handler is what stays narrow.
    const register = () => navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(() => {});
    if (document.readyState === "complete") register();
    else {
      window.addEventListener("load", register, { once: true });
      return () => window.removeEventListener("load", register);
    }
  }, []);

  return null;
}
