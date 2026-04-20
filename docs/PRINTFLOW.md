# PrintFlow MES — 3D-printimisettevõtte juhtimissüsteem

**PrintFlow** on AI-CAD lisamoodul 3D-printimise teenusbüroodele, makerspace'idele ja tootmispaikadele. See muudab AI-CAD-i ühekordse mudeli genereerimise tööriistast **täieliku tootmispõhja** — alates klienti RFQ vormist kuni prindi valmimiseni.

## Miks PrintFlow?

USA turul on 3D-printimise MES lahendused fragmenteerunud: Xometry ja Protolabs haaravad suuri B2B kliente, 3YOURMIND ja 3DPrinterOS keskenduvad farm-orkestratsioonile, OctoPrint teeb ühe-printeri juhtimist. **Väikesed ja keskmised teenusebürood (4-20 printerit) jäävad hammasrataste vahele** — nad kasutavad Excelit hinnastamiseks, WhatsAppi kliendiga suhtlemiseks ja Visuaalse Kontrolli meetodit "OEE" mõõtmiseks.

PrintFlow lahendab selle ühes süsteemis:

| Mured | PrintFlow vastus |
| --- | --- |
| "Pakkumise tegemine võtab 2h" | Instant Quote Engine: upload → hind < 10s |
| "30% töid ebaõnnestub aluse-keel'u või tugede pärast" | DFM analüüs STL-il, severity BLOCK/WARN/OK |
| "Ei tea, mis printer seisab" | Real-time SSE stream, OEE dashboard |
| "Klient küsib staatust iga tund" | Customer portal + webhooki Slacki |
| "Filament lõppes keset tööd" | Spool inventory + low-stock alerts |

## Arhitektuur

```
                     ┌───────────────┐
  RFQ vorm (avalik)  │   Frontend    │
  /p/:slug  ────────▶│   Angular 18  │
                     │   /factory    │
                     └───────┬───────┘
                             │ JWT/REST + SSE
                             ▼
  ┌──────────────────────────────────────────────┐
  │        Spring Boot Backend (8080)            │
  │  ┌──────────┐  ┌──────────┐  ┌────────────┐ │
  │  │ Quote    │  │ Printer  │  │ Job        │ │
  │  │ Service  │─▶│ Farm     │─▶│ Scheduler  │ │
  │  └────┬─────┘  └────┬─────┘  └─────┬──────┘ │
  │       │             │              │        │
  │  ┌────▼─────────────▼──────────────▼─────┐  │
  │  │  PostgreSQL + Flyway V4 migration    │  │
  │  └───────────────────────────────────────┘  │
  └──────┬──────────────────┬────────────────────┘
         │                  │
         ▼                  ▼
   ┌─────────┐        ┌───────────┐
   │ Worker  │        │  Slicer   │
   │ /dfm    │        │ /slice    │
   │ /nest   │        │ Prusa CLI │
   │ trimesh │        │           │
   └─────────┘        └───────────┘
```

## Domeenimudel

Kõik PrintFlow olemid on **organizatsiooni-scoped** (multi-tenant). OrganizationContext resolvib kasutajajärgi praeguse organisatsiooni; uus kasutaja saab automaatselt "Default Print Shop" organisatsiooni.

Põhiolemid (vt. [Flyway V4](../backend/src/main/resources/db/migration/V4__printflow.sql)):

- `organizations` + `organization_members` — multi-tenant core, rollid OWNER/ADMIN/OPERATOR/VIEWER
- `customers` — B2B/B2C kliendid koos maksu-infoga
- `materials` — PLA/PETG/ABS jt koos hinna, tiheduse, min-wall ja setup-fee-ga
- `filament_spools` — individuaalsed spoolid koos massiga, auto-status EMPTY/PARTIAL/FULL
- `printers` — farm inventory koos adapter_type ja current_job'iga
- `printer_events` — SSE event log
- `quotes` + `quote_lines` — pakkumised (DRAFT → SENT → ACCEPTED → EXPIRED)
- `print_jobs` — tööjärjekord (QUEUED → PRINTING → DONE/FAILED)
- `dfm_reports` — salvestatud DFM tulemused igale uploadile
- `build_plates` — grupeeritud tööd ühele plaadile
- `rfqs` — public intake, klient täidab ise
- `webhook_subscriptions` — ERP / Slack integratsioonid

## REST API pinnamark

Kõik endpointid on `/api/printflow/*` alla (vt. [PRINTFLOW_ARCHITECTURE.md](./PRINTFLOW_ARCHITECTURE.md)):

```
# Analytics / KPI
GET  /analytics/kpi                    → revenue, OEE, success_rate, jobs_queued
GET  /analytics/revenue?days=30        → päevast päeva tulu
GET  /analytics/top-materials          → top 10 materjali 90 päeva jooksul

# Quotes
POST /quotes      (multipart: file+material+qty+rush+margin) → instant quote
GET  /quotes                                                 → list
GET  /quotes/{id}                                            → detail
POST /quotes/{id}/accept                                     → loob PrintJob'id

# Printers
GET  /printers                         → farm
POST /printers                         → lisa (ADMIN)
POST /printers/{id}/pause|resume|cancel
GET  /printers/events/stream           → SSE real-time events
POST /printers/heartbeat               → adapter heartbeat callback

# Jobs
GET  /jobs?status=QUEUED
POST /jobs/{id}/cancel
POST /jobs/{id}/priority  {priority: 100}

# Materials & inventory
CRUD /materials
CRUD /spools + GET /spools/low-stock?threshold_g=100

# Customers & RFQ
CRUD /customers
GET  /customers/{id}/quotes
POST /rfq/public/{org-slug}  (PUBLIC, ei nõua auth)
GET  /rfq
POST /rfq/{id}/status

# Webhooks
CRUD /webhooks   (events: job.complete,quote.accepted,spool.low,...)

# DFM
POST /dfm  (proxy worker /dfm-le, kui frontend soovib analüüsi ilma quote'i loomata)
```

## Hinnastamise valem

`PricingService.java`:

```
filament_cost   = (volume_cm3 * density_g_cm3 / 1000) * price_per_kg
machine_cost    = (print_time_sec / 3600) * hourly_rate    # hourly_rate = 2.50 €/h default
base_cost       = max(filament_cost + machine_cost, setup_fee)
unit_price      = base_cost * (1 + margin_pct/100) * (rush ? 1.3 : 1.0)
volume_discount = qty >= 100 ? 15% : qty >= 50 ? 10% : qty >= 10 ? 5% : 0%
line_total      = unit_price * qty * (1 - volume_discount)
```

## DFM algoritm (Worker `/dfm`)

`worker/printflow.py` kasutab **trimesh**'i ja tuvastab:

1. **BBOX_OVER** (BLOCK) — kui mudel ei mahu printeri build volume'i (rotation-aware)
2. **OVERHANG** (WARN kui >25%, INFO kui >5%) — face normales, `nz < -cos(max_overhang_deg)` → overhang pindala %
3. **THIN_WALL** (WARN) — efektiivne paksus `2V/A` < `material.min_wall_mm`
4. **UNSTABLE** (WARN) — `height / min(base_x, base_y) > 3.5`
5. **SMALL_FEATURE** (WARN) — `min(x,y,z) < 2 * min_wall_mm`
6. **NOT_WATERTIGHT** (WARN) — `mesh.is_watertight == False`

Kui mis iganes probleem on **BLOCK**, keeldub Spring automaatselt pakkumise loomisest. Frontend näitab operaatorile konkreetset loendit.

## Job Scheduler

Spring `@Scheduled` iga 15 sekundi järel (konfigureeritav `app.printflow.scheduler.heartbeat-ms`):

1. Võtab iga IDLE printeri
2. Leiab `findNextQueued()` — prioriteedi-järgi järgmise QUEUED töö
3. Kontrollib **materjali-perekonna sobivuse** (printer toetab PLA aga job vajab PETG → skip)
4. Kui on sobiv job: `printer.status = PRINTING`, `job.status = PRINTING`, dispatcheris adapter (Mock/Bambu/Klipper/OctoPrint/PrusaConnect)
5. Iga heartbeat'iga: `MockPrinterAdapter` simuleerib 5-12% progressi/tick, kuni `progress_pct = 100` → `completedJob = true` → PrinterService registreerib DONE

**Materjali-kogus mahaarvamine:** kui `PrintJob` completab, tõmmatakse `weight_g` automaatselt `FilamentSpool.mass_remaining_g`-st. Low-stock alert saadetakse webhooki kaudu.

## Customer portal — public RFQ

Avalik URL: `https://<host>/#/p/<org-slug>`

Klient täidab nime, e-maili, kirjelduse (koos soovitava materjali ja tähtajaga) ning saab response: "Täname! Võtame ühendust 24h jooksul. Päringu ID: #1234".

Operaator näeb RFQ postkasti `/factory` UI-s "RFQ postkast" vahekaardil ja saab märkida NEW → IN_REVIEW → QUOTED.

## Keskkonnamuutujad

Uusi muutujaid PrintFlow ei lisa — kõik kasutab olemasolevat DB ja API konfiguratsiooni. Optional:

- `app.printflow.scheduler.heartbeat-ms` — scheduler interval (default 15000)
- `app.printflow.pricing.hourly-rate-eur` — masina tunnihind (default 2.50)

## Helm / K8s

PrintFlow jookseb olemasolevas backend + worker podis. Migratsioon V4 käivitub automaatselt Flyway kaudu esimesel käivitusel.

**Worker lisab 2 dependensi** (vt. `worker/requirements.txt`):
```
trimesh==4.4.9
rectpack==0.2.2
```

## Roadmap V1.1 → V2

- **V1.1 (2 kuud):** Bambu / Klipper / OctoPrint / PrusaConnect adapterid päriselt (praegu ainult MockAdapter)
- **V1.2 (3 kuud):** Stripe Checkout aktsepteeritud quote'ide jaoks, automaatne arve (Omniva/DHL saatja)
- **V1.3 (4 kuud):** Multi-tenancy eraldi `x-org-id` headeriga (OWNER saab vahetada organisatsioone)
- **V2.0 (8 kuud):** AI pricing intelligence — anomaaliate tuvastus ("see job on 3× kallim kui sarnased"), konkurentide benchmark, automaatne margin-optimize

## Testimine

Backend:
```bash
cd backend
./gradlew test --tests "ee.krerte.cad.printflow.*"
```

Worker:
```bash
cd worker
pytest test_printflow.py
```

## Kuidas alustada

1. `docker compose up -d db worker slicer`
2. `cd backend && ./gradlew bootRun`
3. `cd frontend && npm run start`
4. Ava `http://localhost:4200/#/factory`
5. Lisa esimene materjal + printer + klient
6. Upload STL → saad instant quote

---

*PrintFlow = MES + Quote Engine + Customer Portal — kõik ühes, eesti keeles.*
