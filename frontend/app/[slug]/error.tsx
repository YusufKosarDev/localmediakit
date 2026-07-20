"use client";

// Renders when the page could not be generated (e.g. the slug is not cached yet
// and the backend is unreachable). The failed render is not cached, so a retry
// or a later visit recovers cleanly.
export default function KitError({ reset }: { error: Error; reset: () => void }) {
  return (
    <main className="grid min-h-screen place-items-center px-6 text-center">
      <div>
        <p className="text-6xl" aria-hidden>
          ⏳
        </p>
        <h1 className="mt-4 text-2xl font-semibold tracking-tight">
          Sayfa su anda goruntulenemiyor
        </h1>
        <p className="mx-auto mt-2 max-w-md text-muted">
          Gecici bir sorun olustu. Birkac saniye icinde tekrar deneyin.
        </p>
        <button
          onClick={() => reset()}
          className="mt-6 inline-flex h-10 items-center rounded-xl border border-line bg-surface px-4 font-medium transition-colors hover:bg-page"
        >
          Tekrar dene
        </button>
      </div>
    </main>
  );
}
