import Link from "next/link";
import { ArrowRight, BarChart3, Users, Handshake, ShieldCheck } from "lucide-react";
import { Card } from "@/app/_components/ui";

const FEATURES = [
  {
    icon: BarChart3,
    title: "Istatistik & etkilesim",
    body: "Platform basina takipci, ortalama izlenme ve platforma ozel etkilesim orani — trend rozetleriyle.",
  },
  {
    icon: Users,
    title: "Kitle demografisi",
    body: "Yas, cinsiyet ve ulke dagilimi; markaya kime ulastigini net gosterir.",
  },
  {
    icon: Handshake,
    title: "Marka isbirlikleri",
    body: "Gecmis kampanyalar ve sonuclariyla sosyal kanit vitrini.",
  },
];

export default function Home() {
  return (
    <main className="mx-auto flex min-h-screen max-w-5xl flex-col px-6">
      <header className="flex items-center justify-between py-6">
        <div className="flex items-center gap-2">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-strong text-sm font-bold text-white">
            LM
          </span>
          <span className="font-semibold tracking-tight">LocalMediaKit</span>
        </div>
        <nav className="flex items-center gap-1 text-sm">
          <Link href="/login" className="rounded-lg px-3 py-2 text-muted transition-colors hover:text-fg">
            Giris
          </Link>
          <Link
            href="/register"
            className="rounded-lg bg-fg px-3 py-2 font-medium text-page transition-opacity hover:opacity-90"
          >
            Kayit ol
          </Link>
        </nav>
      </header>

      <section className="flex flex-col items-center py-16 text-center sm:py-24">
        <span className="mb-5 inline-flex items-center gap-2 rounded-full border border-line bg-surface px-3 py-1 text-xs text-muted">
          <span className="h-1.5 w-1.5 rounded-full bg-success" />
          Canli · edge&apos;den servis edilir
        </span>
        <h1 className="max-w-2xl text-4xl font-semibold tracking-tight sm:text-6xl">
          Medya kitiniz, markaya <span className="text-brand">hazir</span> bir link.
        </h1>
        <p className="mt-5 max-w-xl text-lg text-muted">
          Istatistik, etkilesim orani, demografi ve marka isbirliklerini tek, sik bir
          sayfada toplayin ve markalarla paylasin.
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <Link
            href="/demo"
            className="inline-flex h-11 items-center gap-2 rounded-xl bg-brand-strong px-5 font-medium text-white shadow-sm transition-opacity hover:opacity-90"
          >
            Ornek medya kitini gor <ArrowRight className="h-4 w-4" />
          </Link>
          <Link
            href="/login"
            className="inline-flex h-11 items-center gap-2 rounded-xl border border-line bg-surface px-5 font-medium transition-colors hover:bg-page"
          >
            Demo olarak gez
          </Link>
        </div>
      </section>

      <section className="grid gap-4 pb-16 sm:grid-cols-3">
        {FEATURES.map((f) => (
          <Card key={f.title} className="p-6">
            <div className="mb-4 grid h-10 w-10 place-items-center rounded-xl bg-brand-weak text-brand">
              <f.icon className="h-5 w-5" />
            </div>
            <h3 className="font-semibold">{f.title}</h3>
            <p className="mt-1.5 text-sm leading-relaxed text-muted">{f.body}</p>
          </Card>
        ))}
      </section>

      <footer className="mt-auto flex flex-wrap items-center gap-2 border-t border-line py-6 text-xs text-faint">
        <ShieldCheck className="h-3.5 w-3.5" />
        Ucretsiz medya kiti araci — tum ozellikler herkese acik.
      </footer>
    </main>
  );
}
