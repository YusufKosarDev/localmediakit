import "@testing-library/jest-dom/vitest";
import { afterEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";

// Unmount React trees and drop fetch stubs between tests so cases stay
// isolated. Both calls are needed: restoreAllMocks puts the original
// implementations back, and clearAllMocks drops the recorded calls. The base
// fetch below is itself a vi.fn(), so tests spy on a mock; without the clear,
// its call history outlives the test that made it and an assertion looking
// for "the PUT this test sent" can find one from an earlier test instead.
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

// A default no-op fetch so fire-and-forget beacons (TrackView) never error in
// jsdom; individual tests override this with vi.spyOn for the calls they assert.
globalThis.fetch = vi.fn(() =>
  Promise.resolve(new Response(null, { status: 204 }))
) as unknown as typeof fetch;
