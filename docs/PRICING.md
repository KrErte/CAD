# Hinnakiri — TehisAI CAD

## Kolm segmenti

### 1. Tegijad (Makers)

| Plaan | Hind | Generatsioonid/kuu | AI ülevaated/kuu | Meshy/kuu |
|-------|------|---------------------|-------------------|-----------|
| **Maker** | Tasuta | 100 | 30 | 10 |
| **Creator** | 29.99 €/kuu | 500 | 150 | 50 |

**Maker** — kõik mallid, eestikeelne AI, metrika (kaal, aeg, mõõdud). Isiklikuks kasutuseks.

**Creator** — kõik Maker omadused + Darwin CAD, STEP-eksport, Freeform Python-gen, ajalugu + re-download, prioriteetne tugi. Kommertslitsents.

### 2. Prindibürood (Print Bureaus)

| Plaan | Hind | Tellimusi/kuu | Printereid |
|-------|------|---------------|------------|
| **Starter** | 49 €/kuu | 50 | 1 |
| **Studio** | 149 €/kuu | 500 | 10 |
| **Factory** | 399 €/kuu | Piiramatu | Piiramatu |
| **Enterprise** | Kokkulepe | Piiramatu | Piiramatu |

**Starter** — instant quoting, DFM analüüs, üks printer.

**Studio** — kõik Starter omadused + 10 printerit, tööjärjekord, materjali-inventar.

**Factory** — piiramatu printerid ja tellimused, SSE real-time stream, webhook integratsioonid.

**Enterprise** — kõik Factory omadused + kohandatud SLA, pühendatud tugi, on-prem variant.

### 3. Arendajad (Developers)

| Plaan | Hind | Generatsioonid | Rate limit |
|-------|------|----------------|------------|
| **Trial** | Tasuta / 14 päeva | 500 kokku | 60 req/min |
| **Growth** | 79 €/kuu | 1000/kuu | 60 req/min |
| **Business** | 249 €/kuu | 5000/kuu | 300 req/min |

**Trial** — 14-päevane proovperiood, 500 generatsiooni kokku, REST API ligipääs.

**Growth** — kommertslitsents, REST API, 1000 generatsiooni kuus.

**Business** — prioriteetne tugi (4h), usage analytics, 5000 generatsiooni kuus.

## Demo režiim

Anonüümsed kasutajad saavad proovida **2 generatsiooni päevas** ilma registreerimata.
Piirangud: ainult mallipõhine genereerimine, ei AI ülevaadet, ei Meshy vabavormi.
IP-põhine piiramine SHA-256 räsiga.

## Tehniline ülevaade

- Backend: `ee.krerte.cad.pricing` pakett
- Limiitide jälgimine: Redis (ConcurrentHashMap fallback)
- Migratsioon: V9 — `plan` veeru laiendamine VARCHAR(32)
- Claude mudeli jaotus: Haiku (intent parsing + ranking), Sonnet (design review)
- Demo endpoint: `POST /api/demo/generate` (auth ei nõuta)
- Hinnakirja endpoint: `GET /api/pricing/plans` (avalik JSON)
