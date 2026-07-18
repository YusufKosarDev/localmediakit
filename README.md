# LocalMediaKit

Egitim / portfolyo projesi. Icerik ureticileri icin canli medya kiti platformu.
Odeme akisi Stripe **test mode** ile calisir; gercek odeme alinmaz.

Durum: kayit/giris (JWT), medya kiti CRUD + slug yonetimi, publish + immutable
versiyonlama ve edge-cached public sayfalar calisiyor.

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
