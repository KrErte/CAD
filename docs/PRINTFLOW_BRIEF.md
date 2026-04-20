# PrintFlow — Product Brief

*Ühe-lause-visioon: "Sinu 3D-print-ettevõtte täisaegne tootmisjuht,
kes ei maga ja ei eksi Exceli valemites."*

## Toote nimi

**PrintFlow** — MES / Instant-Quote / Fleet-Management moodul AI-CAD
platvormis. Sihtturg: 3D-print-ettevõtted, service bureau'd, ülikoolide
maker-spacid, haigla-prototüübilaborid, kinnisvarahoolduse firmad.

## Probleem (kelle valu lahendame)

**Persona 1 — Mart (service bureau omanik, 4 printerit)**
- Iga päev 10-15 STL-i meilitsi, iga üks: ava Prusaslicer, slice, kirjuta
  kliendile tagasi hind. **2-4 tundi päevas ainult quote'ide kirjutamiseks.**
- Ei tea täpselt, millist printerit mille peal kasutada — "see kes esimesena
  vabaneb".
- PLA ja PETG sampled across 8 värvi, 14 spooli — millal otsa saab? Keegi ei
  tea enne kui on pool prindist tühi.

**Persona 2 — Kati (ülikooli maker-space koordinaator, 8 printerit)**
- Tudengid saadavad SketchUp-i failid, mis ei ole watertight. Print'i
  ebaõnnestumine 30% jõuab sinnamaani.
- Ei ole ühtegi raportit dekaanile: "mis on meie printerite tulundus / kasutus".

**Persona 3 — Raimo (tööstusklient, kes tellib)**
- Saadab 50 tk sama osa kuus mitu korda, iga kord ootab 2 tundi hinnale.
- Tahab B2B portaalis sisse logida, üks-klikk-tellimus "tee veel 50 tükki".

## Väärtuspakkumine

1. **5 sekundit STL → hind** — klient ei pea ootama, sa ei pea kirjutama.
2. **DFM enne printi** — hoiatused "sein on 0.8mm, soovitus vähemalt 1.2mm
   PLA jaoks" ilmuvad enne maksmist.
3. **Õige printer õigele tööle** — scheduler match-ib material + build-volume
   + queue-pikkuse.
4. **Material ei saa kunagi ootamatult otsa** — spool-level inventory +
   automaatne restock-alert.
5. **Üks klikk → Stripe invoice → email → job queue** — null käsitsi-kopee.
6. **Dashboard mis näitab kasumit** — kui palju tuleb teenust iga printer
   sisse + mis on edu-määr.

## Edukuse mõõdikud (OKR, Q2-Q3 2026)

| Objective | Key Result |
|---|---|
| Vähenda quote genereerimise aeg | 2 tundi → alla 10 sekundi (P95) |
| Vähenda print failure määr | 30% → alla 10% (DFM warn + preview) |
| Suurenda printerite utilisatsioon | OEE tõus 40% → 65% |
| Vähenda materjali-otsasaamist | 4 kriitilist juhtumit/kuu → alla 1/kuu |
| Vähenda käsitsi-haldamise aeg | 4 h/päev → alla 30 min/päev |
| Enchanced B2B reorder | 20% tellimustest → 50%+ on reorder |

## MVP scope (V1)

### Sees ✅

- STL/3MF/OBJ upload → DFM analyze → slicer preview → hind (EUR) + ETA.
- Printer-farm registreeriming (mock protokoll esialgu + reaalne adapter
  Bambu/Prusa/OctoPrinti jaoks roadmap'is).
- Queue + scheduler (FIFO + priority + material match).
- Material + spool inventory (grammi-level, låg-stock webhook).
- Build-plate nesting (rectangle-pack, MVP).
- Customer list + B2B login (existing auth + rollid).
- RFQ form (klient saadab vähese kontekstiga, sales vastab).
- Analytics dashboard (5 top-KPI: revenue, jobs, success rate, printer
  uptime, top-material).
- Webhook api (job complete, low-stock, new-rfq).
- Multi-tenant: Organization → Members → Printers.

### Pole sees (V2+ roadmap) ⏳

- AI kaamera-defect detect (spaghetti, bed-adhesion fail).
- AR paigaldusjuhised (glasses overlay).
- Voice-order API (Siri/Alexa plugin).
- Post-print sorting bot integration (AM-Flow tüüp).
- ERP two-way sync (Merit, Directo, Envoice).
- SSO (SAML, OIDC).
- Audit log (ISO 9001 compliance).
- Predictive maintenance (belt, nozzle wear).

## Tehniline strateegia

- **Täiesti additive**: ei muuda olemasolevat AI-CAD-i mehaanikat, lisab
  `/api/printflow/*` endpoint'id ja Angular `/factory` route.
- **Taaskasutab**: olemasolev SlicerClient, WorkerClient, User, Design
  entity. Uued tabelid ainult PrintFlow-domeenile.
- **Asynchronous by default**: job scheduler on Spring `@Async`, status
  SSE kaudu.
- **Mock-first printers**: implementeerime `PrinterAdapter` interface +
  `MockPrinterAdapter`. Reaalse Bambu/Prusa/Moonraker-adapteri lisame
  V1.1-s.

## Go-to-market

- Avaliku betasse eesti 5 printerifirmaga (3DKoda, 3DPrinditud, Trimech,
  Kekkonen, PrintHero) — tasuta 3 kuud, feedback.
- Pärast betat: Stripe self-serve, ingliskeelse marketing-sait,
  Product-Hunt launch.
- Konverentsid: Formnext 2026 (Frankfurt), AM Summit Taanis.

## Budget & ajakava (development)

| Sprint | Periood | Scope |
|---|---|---|
| Sprint 0 | Apr 19-26 | Arhitektuur, DB migratsioon, mock printer adapter |
| Sprint 1 | Apr 27 - Mai 10 | Quote engine + DFM + Customer portal |
| Sprint 2 | Mai 11 - 24 | Farm dashboard + Scheduler + Materials |
| Sprint 3 | Mai 25 - Juuni 7 | Nesting + Analytics + Webhooks |
| Sprint 4 | Juuni 8-21 | Reaalne Bambu/Prusa adapter + tests |
| Sprint 5 | Juuni 22 - Juuli 5 | Beta launch, pilot partneritele |

## Riskid

1. **Printeri-integratsioon variandid** — iga tootja oma protokoll. *Mitigation*:
   alusta mock-ist, hiljem adapteri-pattern.
2. **DFM täpsus** — valed hoiatused tapavad kliendi usalduse. *Mitigation*:
   konservatiivsed defaultid, hoiatused (mitte blokeeringud), võimalus
   operaatoril override.
3. **Slicing CPU-intensiivne** — skaleerudes vaja rohkem workeri-instance'id.
   *Mitigation*: HPA Helm-charti juba olemas.
4. **Legacy ERP sync** — Merit/Directo API-d kitsid. *Mitigation*: CSV export
   V1, API V2.
