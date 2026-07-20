import Link from "next/link";

export default function KitNotFound() {
  return (
    <main className="grid min-h-screen place-items-center px-6 text-center">
      <div>
        <p className="text-6xl" aria-hidden>
          🔍
        </p>
        <h1 className="mt-4 text-2xl font-semibold tracking-tight">Bu sayfa yayinda degil</h1>
        <p className="mx-auto mt-2 max-w-sm text-muted">
          Aradiginiz medya kiti bulunamadi ya da henuz yayinlanmamis olabilir.
        </p>
        <Link
          href="/"
          className="mt-6 inline-flex h-10 items-center rounded-xl bg-brand-strong px-4 font-medium text-white shadow-sm transition-opacity hover:opacity-90"
        >
          LocalMediaKit ana sayfasina don
        </Link>
      </div>
    </main>
  );
}
