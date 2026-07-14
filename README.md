# LocalMediaKit

Egitim / portfolyo projesi. Icerik ureticileri icin canli medya kiti platformu.
Odeme akisi Stripe **test mode** ile calisir; gercek odeme alinmaz.

> **Adim 0** — mimari kanit iskeleti. Feature yok (auth/CRUD/UI yok). Tek amac:
> public sayfalarin backend'e bagli olmadan, edge cache'ten servis edildigini ve
> publish aninda on-demand revalidation ile guncellendigini kanitlamak.

## Mimari (Adim 0)

```
Kullanici publish  ──▶  Spring Boot (Render)  ──▶  icerik DB'ye (Neon)
                                     │
                                     └─(secret)─▶  Next.js /api/revalidate  ──▶ revalidateTag
                                                        │
Marka ziyaretci  ──────────────────────────────▶ Vercel Edge (statik HTML)
                        (backend UYUSA BILE calisir)
```

- **backend/** — Java 21 + Spring Boot. `GET /api/public/kits/{slug}`, `POST /api/kits/{slug}/publish`, `/actuator/health`.
- **frontend/** — Next.js App Router. `app/[slug]` (ISR/edge-cached), `app/api/revalidate` (secret korumali).
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
cp .env.example .env.local   # BACKEND_URL + REVALIDATE_SECRET
npm run build && npm run start
```

`http://localhost:3000/demo` acilir. Publish tetikleme:
```
curl -X POST http://localhost:8080/api/kits/demo/publish \
  -H "Content-Type: application/json" \
  -d '{"content":"Yeni icerik"}'
```

## Secret'lar
`.env` / `.env.local` repoya girmez (bkz. .gitignore). Ornekler: `*.env.example`.
