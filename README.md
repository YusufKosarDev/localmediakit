# LocalMediaKit

Egitim / portfolyo projesi. Icerik ureticileri icin canli medya kiti platformu.

> **Odeme notu:** Odeme akisi Stripe **TEST MODE** ile calisir; gercek odeme
> alinmaz, gercek kart kabul edilmez. Odeme sayfasi Stripe'in kendi hosted
> Checkout sayfasidir - bu deployment odeme islemez. Upgrade akisi yalnizca
> oturum acilmis dashboard'dadir; public medya kiti sayfalarinda hicbir
> odeme/fiyat ogesi yoktur.

Durum: kayit/giris (JWT), medya kiti CRUD + slug yonetimi, publish + immutable
versiyonlama, edge-cached public sayfalar, istatistik/engagement/demografi
katmani, marka isbirlikleri vitrini, ziyaretci analitigi ve Stripe test-mode
faturalama (FREE/PRO) calisiyor.

## Mimari

```
Kullanici publish  ──▶  Spring Boot (Render)  ──▶  snapshot DB'ye (Neon)
                                     │                (media_kit_versions)
                                     └─(secret)─▶  Next.js /api/revalidate  ──▶ revalidateTag
                                                        │
Marka ziyaretci  ──────────────────────────────▶ Vercel Edge (statik HTML)
                        (backend UYUSA BILE calisir)
```

- **backend/** — Java 21 + Spring Boot + Flyway.
  - Auth: `POST /api/auth/register|login`, `GET /api/me` (JWT).
  - Kit CRUD: `/api/mediakits` (sahiplik guard'li; slug uretimi, rezerve kelime
    ve cakisma yonetimi; FREE planda 1 kit).
  - Publish: `POST /api/mediakits/{id}/publish` — draft'tan IMMUTABLE snapshot
    (`media_kit_versions.content_json`) uretir, edge revalidation tetikler.
    Versiyon gecmisi ve geri donme: `GET .../versions`, `POST .../versions/{n}/activate`.
  - Public okuma: `GET /api/public/kits/{slug}` — her zaman AKTIF snapshot'tan,
    asla draft'tan degil.
  - Istatistik: `POST|GET /api/mediakits/{id}/stats` — append-only zaman serisi
    (`platform_stats`), 30 gunluk takipci buyume rozeti. Engagement orani
    platform basina ayri formulle hesaplanir (Strategy:
    `EngagementCalculator` + platform implementasyonlari + registry; yeni
    platform = yeni sinif). Demografi: `GET|PUT /api/mediakits/{id}/demographics`.
    Istatistik ve demografi publish aninda snapshot'a DONDURULUR; public sayfa
    canli hesap yapmaz.
  - Marka isbirlikleri: `GET|POST|PUT|DELETE /api/mediakits/{id}/collaborations`
    (`brand_collaborations`, display_order ile vitrin sirasi). Publish aninda
    snapshot'a donar; sira snapshot dizi sirasi olarak korunur.
  - Ziyaretci analitigi: public sayfa render'dan SONRA bloklamayan bir beacon
    atar (`POST /api/track`, best-effort — backend uyuyorsa sessizce duser,
    sayfa edge'den gelmeye devam eder). Ingestion: anonim gunluk donen
    ziyaretci hash'i (ham IP saklanmaz), 30 dk oturum penceresiyle dedup,
    bot/headless filtresi. Sahibe agregasyon `GET /api/mediakits/{id}/analytics`:
    FREE toplam sayac, PRO tekil ziyaretci + gunluk seri + referrer/cihaz
    kirilimi (plan ayrimi PlanPolicy uzerinden).
  - Faturalama (Stripe TEST MODE, graceful-enable): Stripe entegrasyonu
    (hosted Checkout + imza dogrulamali, idempotent webhook + abonelik yasam
    dongusu) tam olarak uygulandi ve testli; STRIPE_SECRET_KEY,
    STRIPE_WEBHOOK_SECRET, STRIPE_PRICE_ID env'leri girildiginde aktiflesir.
    Bu env'ler yokken demo dagitiminda canli gosterim icin dogrudan
    plan-degistirme uclari devrededir (`POST /api/billing/demo-upgrade|
    demo-downgrade`, yalnizca kullanicinin KENDI plani); Stripe aktifken bu
    uclar 403 doner (odeme bypass'i olamaz — testli). FREE: 1 kit + toplam
    sayac + sayfada rozet. PRO: sinirsiz kit + detayli analitik + rozet yok.
    Downgrade'de mevcut yayinlar korunur; FREE limiti yeni olusturmayi ve
    fazla kitlerin yeniden yayinlanmasini engeller. Secret'lar repoya girmez.
- **frontend/** — Next.js App Router.
  - `app/[slug]` — on-demand ISR: ilk ziyarette uretilir, edge'de cache'lenir,
    yalnizca publish aninda yenilenir. Draft degisiklikleri public sayfayi etkilemez.
  - `app/api/revalidate` — secret korumali on-demand revalidation ucu.
  - `app/dashboard` — kit editoru + yayinlama + versiyon gecmisi.
- Local: backend H2 in-memory ile calisir (kurulum sifir). Prod: Neon Postgres (`prod` profili).

## Local calistirma

Backend (JDK 21 gerekli):
```
cd backend
mvn spring-boot:run
```

Frontend:
```
cd frontend
npm install
cp .env.example .env.local   # BACKEND_URL + REVALIDATE_SECRET + NEXT_PUBLIC_BACKEND_URL
npm run build && npm run start
```

`http://localhost:3000` uzerinden kayit olup dashboard'dan kit olusturulur ve
yayinlanir; public sayfa `http://localhost:3000/<slug>` adresinde acilir.
Ornek yayinlanmis sayfa: `http://localhost:3000/demo`.

## Testler

```
cd backend
mvn test
```

Slug uretimi, sahiplik guard'i, plan limiti, publish/versiyonlama ve snapshot
degismezligi testleri dahildir.

## Secret'lar
`.env` / `.env.local` repoya girmez (bkz. .gitignore). Ornekler: `*.env.example`.
