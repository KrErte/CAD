# PrintFlow — Arhitektuur

## 1. Komponentide diagramm

```
┌──────────────────────────────────────────────────────────────────────┐
│                            Browser (Angular 18)                      │
│  ┌────────────────┐  ┌─────────────────┐  ┌────────────────────┐    │
│  │ /factory       │  │ /factory/quote  │  │ /factory/printers  │    │
│  │ dashboard      │  │ (drag-drop STL) │  │ (live SSE stream)  │    │
│  └────────────────┘  └─────────────────┘  └────────────────────┘    │
│  ┌────────────────┐  ┌─────────────────┐  ┌────────────────────┐    │
│  │ /factory/jobs  │  │ /factory/mat    │  │ /factory/customers │    │
│  └────────────────┘  └─────────────────┘  └────────────────────┘    │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ HTTPS (/api/printflow/*)
┌────────────────────────────▼─────────────────────────────────────────┐
│                  Spring Boot 3 Backend (Java 21)                     │
│                                                                      │
│  Auth: JwtAuthFilter (olemas) → OrganizationContext ←── multi-tenant │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                       PrintFlow Moodul                       │    │
│  │                                                              │    │
│  │  QuoteController      PrinterController   MaterialController │    │
│  │         ↓                    ↓                    ↓          │    │
│  │   QuoteService         PrinterService      MaterialService   │    │
│  │         ↓                    ↓                    ↓          │    │
│  │   DFMCoordinator ─→ WorkerClient /dfm                        │    │
│  │   SlicerClient  ─→ Slicer sidecar                            │    │
│  │   JobScheduler  ─→ PrinterAdapter.dispatch()                 │    │
│  │                                                              │    │
│  │  RFQController        CustomerController   JobController     │    │
│  │  WebhookPublisher     AnalyticsController  NestingService    │    │
│  └─────────────────────────────────────────────────────────────┘    │
│              │                 │                 │                   │
│              ▼                 ▼                 ▼                   │
│          Postgres         SSE (EmitterPool)  RedisQueue (hiljem)     │
│          (Flyway V4)                                                 │
└──────────────────────────────────────────────────────────────────────┘
       │                                          │
       ▼                                          ▼
┌──────────────────────┐                ┌────────────────────────────┐
│  Worker (FastAPI)    │                │  Slicer (FastAPI)          │
│  - /generate  (olem) │                │  - /slice  (olemas)        │
│  - /dfm      (UUS)   │                │  - /dfm-preview (UUS)      │
│  - /nest     (UUS)   │                └────────────────────────────┘
└──────────────────────┘                             ▲
         ▲                                           │
         │                                           │
         └─── CadQuery + trimesh                ┌────┴───────────────────┐
                                                │ PrinterAdapter (SPI)   │
                                                │  - MockAdapter         │
                                                │  - BambuAdapter (V1.1) │
                                                │  - MoonrakerAdapter    │
                                                │  - OctoPrintAdapter    │
                                                │  - PrusaConnectAdapter │
                                                └────────────────────────┘
```

## 2. Domeeni-mudel

### 2.1. Organisatsioon (multi-tenant)

Et iga 3D-print-ettevõte saaks oma view'i ja oma kliendid, on juurtabel:

```
Organization (id, name, slug, plan, created_at, owner_user_id)
  └─ OrganizationMember (user_id, organization_id, role) -- OWNER/ADMIN/OPERATOR/VIEWER
```

### 2.2. Customers (B2B/B2C kliendid)

```
Customer (id, organization_id, kind=B2B|B2C, name, email, phone, vat_id,
          billing_address, shipping_address, notes, default_margin_pct)
```

### 2.3. Materials & Spools

```
Material (id, organization_id, name, family=PLA|PETG|ABS|PC|TPU|ASA|NYLON|RESIN,
          price_per_kg_eur, density_g_cm3, default_preset,
          min_wall_thickness_mm, max_overhang_deg, active)

FilamentSpool (id, organization_id, material_id, color, color_hex,
               mass_initial_g, mass_remaining_g, serial_barcode,
               purchased_at, expires_at, vendor, lot,
               assigned_printer_id, status=FULL|PARTIAL|EMPTY|DISPOSED)
```

### 2.4. Printers & Adapters

```
Printer (id, organization_id, name, vendor, model,
         build_volume_x, build_volume_y, build_volume_z,
         supported_materials (jsonb),
         adapter_type=MOCK|BAMBU|MOONRAKER|OCTOPRINT|PRUSA_CONNECT,
         adapter_url, adapter_api_key_encrypted,
         status=IDLE|PRINTING|PAUSED|ERROR|OFFLINE,
         current_job_id, current_temperature_bed, current_temperature_hotend,
         last_heartbeat_at, created_at)

PrinterEvent (id, printer_id, event_type, payload_jsonb, occurred_at)
```

### 2.5. Quotes

```
Quote (id, organization_id, customer_id, status=DRAFT|SENT|ACCEPTED|REJECTED|EXPIRED,
       total_eur, margin_pct, setup_fee_eur, rush_multiplier,
       valid_until, notes, created_at, accepted_at)

QuoteLine (id, quote_id, stl_path_ref, file_name, quantity, material_id,
           infill_pct, layer_height_mm, color, unit_price_eur, total_eur,
           slicer_result_jsonb, dfm_report_id)
```

### 2.6. DFM Reports

```
DFMReport (id, file_path, bbox_x_mm, bbox_y_mm, bbox_z_mm, volume_cm3,
           triangles, is_watertight, self_intersections, min_wall_thickness_mm,
           overhang_area_cm2, overhang_pct, thin_features_count,
           issues_jsonb, severity=OK|WARN|BLOCK, created_at)
```

Issues-struktuur (vt `worker/app.py` `/dfm`):
```json
{
  "kind": "wall_too_thin",
  "severity": "warn",
  "location_mm": [x,y,z],
  "actual_value": 0.8,
  "recommended_min": 1.2,
  "message_et": "Sein 0.8mm — soovitus PLA jaoks vähemalt 1.2mm"
}
```

### 2.7. Jobs & Build Plates

```
PrintJob (id, organization_id, quote_id, quote_line_id, quantity_remaining,
          material_id, spool_id, printer_id, priority (0-100),
          gcode_path_ref, estimated_time_sec, estimated_filament_g,
          status=QUEUED|ASSIGNED|PRINTING|PAUSED|DONE|FAILED|CANCELLED,
          started_at, finished_at, failure_reason, retries)

BuildPlate (id, organization_id, printer_id, material_id, status=PLANNED|PRINTING|DONE|FAILED,
            plate_x_mm, plate_y_mm, nesting_jsonb, job_ids_jsonb,
            created_at, printed_at)
```

### 2.8. RFQ

```
Rfq (id, organization_id, contact_name, contact_email, description,
     attachments_jsonb, quantity_hint, material_hint, deadline,
     status=NEW|IN_REVIEW|QUOTED|LOST|WON, quote_id, created_at)
```

## 3. REST API

Kõik endpoindid on autentitud olemasoleva `JwtAuthFilter`-iga ja nõuavad
rolli OWNER või OPERATOR (viewer tohib ainult GET).

### Quote Engine

```
POST /api/printflow/quotes
  multipart: stl (file) + material_id + quantity + infill + color + customer_id(opt)
  -> 200 { quoteId, total_eur, line: { dfm, slicer, unit_price, total } }

GET  /api/printflow/quotes/{id}
POST /api/printflow/quotes/{id}/lines       — lisa line
POST /api/printflow/quotes/{id}/send        — klientile mail + token-link
POST /api/printflow/quotes/{id}/accept      — klient klikkis nõus
POST /api/printflow/quotes/{id}/convert     — loo PrintJob'id
```

### DFM

```
POST /api/printflow/dfm        — faili üles → raport (ilma quote'ita)
GET  /api/printflow/dfm/{id}
```

### Printers

```
GET    /api/printflow/printers
POST   /api/printflow/printers
PUT    /api/printflow/printers/{id}
DELETE /api/printflow/printers/{id}
POST   /api/printflow/printers/{id}/heartbeat          — adapter-poke
POST   /api/printflow/printers/{id}/commands/{cmd}     — pause/resume/cancel
GET    /api/printflow/printers/events/stream           — SSE, real-time status
```

### Materials & Spools

```
GET    /api/printflow/materials
POST   /api/printflow/materials
PUT    /api/printflow/materials/{id}
DELETE /api/printflow/materials/{id}

GET    /api/printflow/spools
POST   /api/printflow/spools
PUT    /api/printflow/spools/{id}         — kaaluuuendus (barcode scan)
GET    /api/printflow/spools/low-stock
```

### Jobs & Scheduling

```
GET    /api/printflow/jobs?status=QUEUED
POST   /api/printflow/jobs/{id}/cancel
POST   /api/printflow/jobs/{id}/priority  { priority: 0-100 }
POST   /api/printflow/jobs/{id}/reassign  { printerId }
GET    /api/printflow/schedule/next-for/{printerId}
POST   /api/printflow/jobs/{id}/complete  { success: true|false, reason? }
```

### Customers

```
GET    /api/printflow/customers
POST   /api/printflow/customers
PUT    /api/printflow/customers/{id}
GET    /api/printflow/customers/{id}/orders
```

### RFQ

```
POST   /api/printflow/rfq          — public (klient saadab)
GET    /api/printflow/rfq          — list (admin)
POST   /api/printflow/rfq/{id}/quote — convert to quote
```

### Analytics

```
GET    /api/printflow/analytics/kpi           — top KPI cards
GET    /api/printflow/analytics/revenue?days=30
GET    /api/printflow/analytics/printer-oee
GET    /api/printflow/analytics/top-materials
```

### Webhooks

```
POST   /api/printflow/webhooks/subscribe
POST   /api/printflow/webhooks/test/{id}
```

## 4. Andmevoog — "Klient saadab STL"

```
1. Klient avab https://app.ee/factory/quote
2. Drag-drop STL → POST /api/printflow/quotes (multipart)
3. QuoteService:
     a) Salvestab STL (tempstore)
     b) WorkerClient.dfm(stl) → DFMReport
     c) Kui BLOCK → tagastab hoiatuse, ei lõika
     d) SlicerClient.slice(stl, preset)
     e) Arvutab hinda:
          base = filament_cost + (print_time_sec/3600 * hourly_rate)
          setup_fee = material.setup_fee
          unit = (base + setup) * margin
          total = unit * quantity - volume_discount(quantity)
     f) Salvestab Quote + QuoteLine
4. Tagastab {quote, line, dfm, preview} JSON
5. Klient klikib "Accept"
     → /api/printflow/quotes/{id}/accept
     → loob PrintJob'id (1 iga line × quantity, aga BuildPlate'i hiljem grupeerib)
     → JobScheduler paneb queue'sse
6. JobScheduler trigger'itakse iga 30 sek + uue printeri heartbeat'i peale:
     a) Leia IDLE printer, mille supported_materials hõlmab job.material
     b) Leia job prioriteedi järjekorras
     c) Nesting: leia teised samal material+printeriga jobid, pane BuildPlate'ile
     d) PrinterAdapter.dispatch(gcode, plate)
7. Printer → PrinterAdapter.heartbeat() → PrinterEvent → SSE stream → UI
8. Prindi lõppedes: PrinterAdapter.onJobComplete → PrintJob.DONE
     → MaterialService.deductSpool(filament_g)
     → WebhookPublisher.fire("job.complete", payload)
```

## 5. Printer Adapter SPI

Et mitte lukustada end ühe printer-brändi külge, on `PrinterAdapter`
interface:

```java
public interface PrinterAdapter {
    PrinterStatus status();
    String dispatch(byte[] gcode, String jobName);  // returns adapter-job-id
    void pause();
    void resume();
    void cancel();
    void refreshTemps();
}
```

Implementatsioonid:
- **MockPrinterAdapter** — simuleerib, kasutame testide + demo jaoks.
- **MoonrakerAdapter** (V1.1) — Klipper/Moonraker JSON-RPC.
- **OctoPrintAdapter** (V1.1) — REST + API key.
- **BambuAdapter** (V1.2) — MQTT + FTP.
- **PrusaConnectAdapter** (V1.2) — REST v1.

## 6. Event-bus ja SSE

`PrinterEvent`-tabel on append-only audit log. Lisaks:

```java
@Component
class PrinterEventEmitterPool {
    private Map<Long, List<SseEmitter>> byOrg = ...;
    public void publish(PrinterEvent ev) { /* fan-out */ }
}
```

Frontend subscribib `/api/printflow/printers/events/stream?orgId=X` → saab
JSON-idna: `{printerId, status, currentJobId, bedC, hotendC, progressPct}`.

## 7. Hinnakalkulatsioon

```
line.total_eur =
    max(
        setup_fee,
        (filament_g * material.price_per_kg_eur / 1000)
        + (print_time_sec / 3600) * printer_hourly_rate
    )
    * (1 + margin_pct/100)
    * quantity
    * (1 - volume_discount(quantity))
```

`volume_discount`: 1-4 tk → 0%, 5-19 → 5%, 20-99 → 10%, 100+ → 15%.

`rush_multiplier` (kui klient valib "24h rush"): × 1.5.

## 8. DFM algoritm (worker-is)

`POST /dfm` (multipart: stl):

```python
mesh = trimesh.load(stl)

report = {
  "bbox_mm": mesh.bounds.tolist(),
  "volume_cm3": mesh.volume / 1000,
  "is_watertight": mesh.is_watertight,
  "triangles": len(mesh.faces),
  "self_intersections": len(trimesh.intersections.mesh_intersections(mesh, mesh)),
  "min_wall_thickness_mm": _thinness_probe(mesh),   # ray-cast pairs
  "overhang_area_cm2": _overhang_sum(mesh, angle=45),
  "overhang_pct": _overhang_pct(mesh),
  "thin_features_count": _count_thin_features(mesh, 0.4),
  "issues": [...],  # per-issue rules
}
```

Seadmed (PLA, PETG, resin) määravad omad piirid - vt `worker/dfm_rules.py`.

## 9. Build-plate nesting

`POST /nest`:
```json
{
  "plate_x_mm": 250, "plate_y_mm": 210,
  "parts": [
    {"id": 1, "bbox_x": 60, "bbox_y": 40, "qty": 3},
    {"id": 2, "bbox_x": 80, "bbox_y": 30, "qty": 2}
  ]
}
```

Kasutame `rectpack` (Maximal Rectangles Best Area Fit) algoritmi — piisav MVP
jaoks. V2-s saab 3D-pakkimise (height-stacking) lisada.

## 10. Turvalisus ja tenant-isolation

Kõikidel päringutel kontrollime `OrganizationContext`-is:
- Kui user pole member → 403.
- Iga Repo-meetod filtreerib `WHERE organization_id = :orgId`.

Lisaks: `Principal` → `User` → loetakse `organization_members` tabelist
aktiivne organization (kasutaja vaikimisi). Multi-org kasutaja saab
lülituda UI-s.

## 11. Skaleerumine

- Slicer ja Worker on horisontaalselt skaleeritavad (HPA Helm-chartis juba
  olemas). Ühe slicer-instansi peal 1 slice = 10-60 sekundit; 10 requesti/min
  tähendab ~1 instance.
- Scheduler on Spring `@Scheduled(fixedDelay=30000)` — üks instance (leader
  election roadmap-is, aga V1 jaoks on üks pod okei).
- Postgres andmebaas: PrintFlow tabelid ~10k rida kuu, normaliseeritud, JSONB
  audit-logile.

## 12. Ops & observability

- Metrikad `/actuator/metrics`:
  - `printflow.quote.duration` (histogram)
  - `printflow.dfm.severity{severity}` (counter)
  - `printflow.job.status{status}` (gauge)
  - `printflow.printer.oee{printerId}` (gauge)
- Sentry breadcrumbid quote/dfm/scheduler kõnede kohta.
- Actuator health: `/actuator/health/printflow-scheduler`.
