# PrintFlow — USA turu analüüs ja gap analysis

*Koostatud: aprill 2026*
*Autor: Olen, AI-CAD tiim*

## 1. Sissejuhatus

AI-CAD (olemasolev toode) on loodud **lõpptarbijale** — inimene kirjutab eesti
keeles mida tal vaja on, saab STL-i ja tellib print-teenusest. See on B2C
"text-to-part" prosumer-toode.

Aga **3D-print-ettevõtted ise** (print-farmid, service-bureau'd, tootmislinnad)
kasutavad hoopis teistsugust tarkvara. Nende päevane valu on:

1. **Quote genereerimine** — klient saadab STL-i meilile, operaator lõikab
   käsitsi slicer'is ja kirjutab hinna tagasi → 30 min – 4 tundi viivitus,
   inimkulu.
2. **DFM-kontroll** — kliendi fail on vigane (liiga õhukesed seinad,
   mittesuletud mesh, liiga peenikesed detailid) — selgub alles prindi ajal.
3. **Farmihaldus** — 10, 20, 50 printerit, igaühel materjali värvid +
   järjekorrad, kes käsitsi Exceli tabelis haldab.
4. **Materjali inventuur** — "millal jõuab järgmine PETG-tarnimine", keegi
   ei tea täpselt.
5. **Kliendi-haldus** — B2B kliendid soovivad ise logida sisse, näha oma
   tellimuste ajalugu, laadida STL-i ja saada hind kohe.
6. **Raporteerimine** — OEE, tootlikkus, edu-määr, ajaline ROI — keegi ei tea.

Sellise tarkvara nimi tööstuses on **MES** (Manufacturing Execution System)
või **Instant-Quote Portal**. USA-s on see turu-segment juba olemas; Eestis
ja Baltikumis mitte.

## 2. USA turu juhtivad lahendused

### 2.1. Instant Quote Portals (B2C/B2B)

| Tegija | Kirjeldus | Hind | Tugevused | Nõrkused |
|---|---|---|---|---|
| **Xometry** | Marketplace + AI-quote. STL üles → 3 sek hind. | Free quote, 15% marketplace fee | AI-quote engine, 24/7 hinna ette, globaalne võrgustik | Vendor ise ei saa lokaalselt hostida, andmete omandiõigus |
| **Protolabs** | Digital factory + instant DFM. | Enterprise | 20-a. DFM-baas, reaalaja feedback browseris | Suletud platvorm, oma-hostidud ei võimalik |
| **Hubs (Protolabs Network)** | P2P marketplace | Percent fee | Automatiseeritud hindamine | Suletud |
| **Shapeways** | Marketplace + consumer UI | Per-print | Material library, tagasihind | Aeglane, kallis |
| **Fictiv** | B2B, Hiina-tootmise sillaks | Enterprise | Hea RFQ UI, kvaliteedikontroll | Rasketööstus fookus, AM on tibuke |
| **3YOURMIND** | Enterprise AM-software — instant quote + part library | Enterprise, €€€ | Internal AM-quote portal, ERP-integreeritud | Kallis, keeruline paigaldada |

### 2.2. Print-farm ja MES tarkvara

| Tegija | Kirjeldus | Tugevused | Nõrkused |
|---|---|---|---|
| **3DPrinterOS** | Cloud fleet management. Kasutaja-queue, materjali track, multi-tenant. | Paljude printerite tugi (50+), user roles, Google Workspace integratsioon. Haridusvaldkonnas populaarne. | Suletud-cloud (oma-hostitud ei saa), printimise skeemitamine primitiivne. |
| **Formlabs Dashboard** | SLA-printeritele. | Reaalajas järjekord, failure alert | Vendor-lock — ainult Formlabs |
| **Ultimaker Digital Factory** | Cloud FFF farmi jaoks. | Tuigub hästi Ultimaker Cura-ga | Vendor-lock, hakkab kaduma peale Ultimaker→UltiMaker-ühinemist. |
| **Bambu Farm Manager** | Bambu-spetsiifiline. | Hea real-time monitoring, kaamerafeed | Suletud ökosüsteem |
| **Prusa Connect** | Prusa-printerite farm. | Lihtne paigaldus | Ainult Prusa, analüütika nõrk |
| **Markforged Eiger** | Continuous-fiber MES. | Digital warehouse, version control | Ainult Markforged printerid, B2B-Enterprise hind |
| **Materialise Streamics** | Industrial MES, konkurents Oqton-iga. | Standard ISO-9001 workflow, töölaeb SLM/DMLS jaoks | Vägakallis (€100k+), keeruline |
| **Authentise Flows** | MES no-code voog. | Drag-drop workflow builder | €€€ |
| **Oqton FactoryOS** | ML-põhine skeduleerimine. | AI-optimiseerimine, nesting | €€€, cloud-only |
| **Link3D (by Divergent)** | Boeing/SpaceX kasutavad. | Workflow + qualifications | Aerospace-fookus |
| **AM-Flow** | Visiooniga post-print sortimine. | Robotsorteerimine | Riistvara-fookus |
| **OctoPrint** | Open-source, ühe printeri haldus. | Täisavatud, pluginad | Ei ole mõeldud farmile, skaleerumatu |
| **Mainsail/Fluidd** | Klipper frontend, ühe printeri haldus. | Hea UI | Ainult Klipper |

### 2.3. DFM ja eelanalüüs

| Tegija | Kirjeldus | Tugevused | Nõrkused |
|---|---|---|---|
| **Netfabb** (Autodesk) | STL-repair + DFM | De-facto standard | Kallis, desktop-only |
| **Materialise Magics** | Industrial slicing + repair | Tööstusharu liider | Kallis, suletud |
| **Meshmixer** | Free STL tool | Tasuta | Lõpetatud, ei arenda |
| **3DPrinterOS Cloud Slicer** | Browser STL check | Integreeritud | Pealiskaudne DFM |
| **Fictiv DFM Analyzer** | Browser DFM nagu Protolabs | Automaatne feedback | Suletud, ei saa eraldi osta |

## 3. Gap analysis — mis meil (AI-CAD) on ja mis mitte

### 3.1. Meil ON ✅

- Spring Boot + Angular + Postgres stack (moodsa enterprise-standardi järgi).
- Claude API integratsioon (tekst → CAD spec).
- CadQuery worker (parametriline generering).
- PrusaSlicer CLI sidecar (print-time + filament mass + hind).
- AI Design Review (Claude Vision).
- Kasutajakontod, JWT, Stripe, kvoodid.
- Gallerii, versioneerimine, lihtsad "print orders" tellimused.
- Docker, Helm, k8s, GitHub Actions CI.

### 3.2. Meil EI OLE ❌ — see, mida iga print-ettevõte vajab

| Funktsionaalsus | Kus USA-s olemas | Mis meil peaks olema |
|---|---|---|
| **Instant quote STL upload'ist** | Xometry, Protolabs, 3YOURMIND | `POST /api/printflow/quotes` — fail üles → 5 sek hind tagasi. Konfigureeritav margin + setup + volume discount. |
| **DFM analyzer** (wall thickness, overhangs, small features, non-manifold mesh) | Netfabb, Fictiv, Protolabs | Worker'isse `/dfm` endpoint, mis kasutab trimesh-i + CadQuery-t. Hoiatused enne tellimist. |
| **Printer-farm dashboard** | 3DPrinterOS, Bambu Farm Manager | `/factory/printers` — registri kõik printerid, reaalaja staatus, käesolev töö, ETA. |
| **Job queue + scheduler** | 3DPrinterOS, Oqton | Prioritsusega queue, automaatne material+build-volume match, optimum printer selection. |
| **Material / spool inventory** | 3DPrinterOS, Streamics | Spool-level tracking (EAN, grammi jäänud, kasutuskordi, spoolikulumus). Low-stock alert. |
| **Build-plate nesting** | Materialise, Oqton | 2D packing algoritm: pane 5 väikest osa ühele plaadile → säästa prinditunde. |
| **Customer portal (B2B)** | Xometry, Fictiv | Kliendile login, oma STL-id, tellimuste ajalugu, reorder 1 klikiga. |
| **RFQ workflow** | Xometry (enterprise), Fictiv | Klient: "500 tk selle faili, PETG, must" → sales flagid → custom hind. |
| **Production analytics / OEE** | Oqton, Authentise | Edu-%, uptime, revenue-per-printer, materiali raiskamine, top-5 kulukat tööd. |
| **Printer integration (Bambu / Prusa / OctoPrint / Moonraker)** | 3DPrinterOS, Bambu | Adapter-layer, mis toetab mitut protokolli. Mock + real modes. |
| **Quality control (foto, time-lapse, defect log)** | AM-Flow, 3DPrinterOS | Kaamerafeed, automaatne spaghetti-detect (AI), labelling. |
| **ERP / accounting sync** | Streamics, 3YOURMIND | Export to CSV + webhook to Merit/Directo/Envoice. |
| **Multi-tenant (service-bureau'ile, et igal kliendil oma view)** | Xometry, 3DPrinterOS | Organizations + members + role-based access. |

## 4. Võimalus Eesti ja Baltikumi (ja EU) turul

Eestis on **~50 3D-print-ettevõtet** (3DKoda, 3DPrinditud, Trimech, Kekkonen,
jt) ja Baltikumis/Põhjamaades **~300-500**. Enamus töötab **Exceli + meilitsi
+ käsitsi-slicer**-stackil. Kogu EU-s on ~5000 AM service bureau'd.

**Probleem**: ükski suur USA SaaS ei tee mõtet 1-5 printeriga ettevõttele:
- 3DPrinterOS hind algab $500/kuu organisatsioonilt.
- Xometry võtab 15% marketplace fee, aga ei anna oma-brandi portaali.
- 3YOURMIND alustab €30k+/aasta.

**AI-CAD-i eelised**:
- Olemas juba Claude + CadQuery + Slicer → "instant quote" funktsioon
  on 1 nädala kaugusel.
- Avatud stack → saab ise hostida (olulised GDPR + tööstusfailide puhul).
- Eestikeelne UI + tugi (selge konkurentsi-eelis Balti turul).
- Modulaarne pricing — hakka $29/kuu-ga, skaleeri printerite arvuga.

## 5. Must-have MVP scope

Järgnev nimekiri on **see, mida ükski 3D-print-ettevõte ilma ei taha**:

1. **Instant Quote Engine** — STL üles → DFM check → slicing → hind + ETA.
2. **DFM Analyzer** — wall thickness, overhang, mesh integrity.
3. **Printer Farm Dashboard** — reaalajas staatus, ETA, käesolev töö.
4. **Job Queue + Scheduler** — material-match + prioriteet.
5. **Material Inventory** — spoolitasemel, low-stock webhook.
6. **Build-plate Nesting** — 2D-pakkimine, optimiseerimine.
7. **Customer Portal** — B2B login + reorder.
8. **Analytics** — OEE, revenue-per-printer, edu-%.
9. **Webhook API** — integreerimine Merit/Envoice/Slack/Discord.
10. **Multi-tenant** — organizations + role-based access.

Ülejäänu (AI-defect-detect, AR-instructions, printeriga otseintegreering) on
**roadmap** — V2+.

## 6. Hinnakujundus (ettepanek)

| Tier | Hind (EUR/kuu) | Kellele | Piirangud |
|---|---|---|---|
| **Solo** | €29 | 1 printer, kuni 20 quote'i/kuu | Single-user |
| **Studio** | €99 | 5 printerit, 200 quote'i/kuu | 3 kasutajat |
| **Farm** | €299 | 25 printerit, piiramata quote'id | 10 kasutajat, white-label |
| **Enterprise** | Custom | 25+ printeri | SSO, SLA, dedicated support |

USA konkurent (3DPrinterOS Studio) algab **$500/kuu** = ~€465. Meil on
~5× soodsam + eestikeelne + GDPR-on-prem.

## 7. Kokkuvõte

See uus moodul **PrintFlow MES** positsioneerib AI-CAD-i täiesti uueks
segmendiks: B2B **AM service bureau automatisatsioon**. See pole enam
"kirjutad eesti keeles detaili" toode — see on "sinu print-ettevõtte
päevase töö automatisaator".

Olemasolev stack (Spring Boot + CadQuery + Slicer) on just see, mis
võimaldab instant-quote ehitada päevade, mitte kuude, jooksul. Järgmine
peatükk kirjeldab **arhitektuuri** ning siis hakkame **implementatsiooniga**.
