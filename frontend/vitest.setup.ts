import "@testing-library/jest-dom/vitest";
import { afterEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";

// Unmount React trees and drop fetch stubs between tests so cases stay isolated.
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

// A default no-op fetch so fire-and-forget beacons (TrackView) never error in
// jsdom; individual tests override this with vi.spyOn for the calls they assert.
globalThis.fetch = vi.fn(() =>
  Promise.resolve(new Response(null, { status: 204 }))
) as unknown as typeof fetch;
