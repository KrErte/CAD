# AI Superpowers

Neljakihiline AI-süsteem AI-CAD peal. Iga kiht eraldi kasulik, koos "magic-moment"
UX mis ei ole üheski teises CAD-tööriistas olemas.

## Arhitektuur

```
┌──────────────────────────────────────────────────────────────────────┐
│  Frontend  /ai-studio  (Angular standalone component)                │
│      · 4-agent council visual                                        │
│      · Generative loop SSE timeline                                  │
│      · DFM heat-map                                                  │
└───────────────┬──────────────────────────────────────────────────────┘
                │  /api/ai/*
┌───────────────▼──────────────────────────────────────────────────────┐
│  Spring Boot backend  ee.krerte.cad.ai package                       │
│                                                                      │
│  ┌────────────────────────┐   ┌─────────────────────────┐            │
│  │ MultiAgentReviewService│   │ GenerativeLoopService   │            │
│  │  4× Claude parallel    │   │  iterate until target   │            │
│  │  + Synthesizer         │   │  SSE emit per step      │            │
│  └───────────┬────────────┘   └──────────────┬──────────┘            │
│              │                                │                      │
│  ┌───────────▼──────────┐    ┌───────────────▼────────────┐          │
│  │ TemplateRagService   │    │ DfmAi (AiController /fix)  │          │
│  │  pg_trgm similarity  │    │  rule-based + LLM combo    │          │
│  └──────────────────────┘    └────────────────────────────┘          │
└───────────────┬──────────────────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────────────┐
│  Python worker                                                       │
│    · dfm.py — deterministlik reegli-audit (< 100ms)                  │
│    · evolve.py, freeform.py — Darwin CAD + vabavormi script-gen      │
└──────────────────────────────────────────────────────────────────────┘
```

## 4 spetsialist-agenti (MultiAgentReview)

`POST /api/ai/review-council` kutsub nelja paralleelset Claude-kutset,
iga oma süsteem-prompti ja kaaluga.

| Persona | Kood | Kaal | Fookus |
|---|---|---|---|
| Struktuuri-insener | `structural` | 0.35 | Koormustaluvus, pinge, hoob, turvamarginaal |
| Prindiprotsessi ekspert | `print` | 0.30 | Overhang'id, sillad, first-layer, orientatsioon |
| Maksumuse-optimeerija | `cost` | 0.20 | Filament + aeg, overengineering |
| Esteetika & ergonoomika | `aesthetics` | 0.15 | Proportsioonid, fillet'id, UX |

Iga agent tagastab `{score, verdict_et, findings[], suggestions[]}` ja nende
vastus salvestatakse `ai_reviews.council` JSONB-veergu. Viies kutse —
**Synthesizer** — saab kõik 4 agenti ette ja toodab koondverdiktid pluss
top-5 prioriseeritud tegevused. Samuti märgib, mitu agenti on sama
muudatuse poolt (`backed_by: ["structural", "print"]`) — see on tähtsam
signaal kui ükskoik ühe agendi soovitus.

**Council score** = kaalutud keskmine:
```
council_score = Σ(persona.weight * persona.score)
```

**Miks see on parem kui üks review?** Ühe-kutse review "keskmistab"
konfliktid näiteks "suurenda seina + vähenda materjali" omavahel
tasakaalustades, peites kasutajalt trade-offi. Multi-agent teeb konflikti
ilmseks: struktuur-insener tahab paksemat seina, maksumuse-optimeerija
õhukamat — kasutaja saab tegeliku informeeritud valiku.

## Generative loop (iterate until perfect)

`POST /api/ai/iterate` — Server-Sent Events stream. Loop kestab kuni:
- `target_reached` (score ≥ 8.5 vaikimisi),
- `max_iter` (MAX_ITER = 5),
- `no_improvement` (score langes > 1.0),
- `no_patch_available` (LLM ei anna enam numbrilisi soovitusi).

Iga samm emiteeritakse SSE-s:
```
event: start   data: {"type":"start","target":8.5,"initial_spec":{...}}
event: review  data: {"type":"review","step":0,"score":6,"verdict_et":"..."}
event: patch   data: {"type":"patch","step":0,"param":"wall_thickness","old":3,"new":5,...}
event: review  data: {"type":"review","step":1,"score":9,"verdict_et":"..."}
event: stop    data: {"type":"stop","reason":"target_reached","final_score":9,...}
```

**Turvamehhanismid:**
- Patch-value clamp'itakse template-skeemi `min`/`max` piiresse, et LLM
  hallutsinatsioon ei crashi workerit.
- Regressiooni-detekt peatab loop'i kohe kui skoor langes — viimase hea
  spec'i tagastame kasutajale.
- MAX_ITER = 5 — keskmine Anthropic'i kulu ~$0.50 per jooks.

**UX:** Angular komponent renderdab iga event'i timeline'ile (tl-item
CSS-klass). Kasutaja näeb elavas ajas, kuidas disain areneb.

## DFM analyzer (reeglipõhine, < 100ms)

`worker/dfm.py` — 5 reegli-perekonda:

| Reegel | Kontrollib |
|---|---|
| `rule_thin_wall` | Wall < 1.2mm kritiline, < 2mm hoiatus; koormus vs paksus |
| `rule_overhang` | hook reach/load, shelf_bracket arm/pipe, hinge_cuts |
| `rule_bridge` | box/enclosure laius > 40mm, vent_slots tihedus |
| `rule_min_feature` | screw_hole < 2mm, gear module < 1.5, snap thickness < 1mm |
| `rule_footprint` | bed 220×220×250 piir |

Vastus:
```json
{
  "template": "shelf_bracket",
  "score": 6.8,
  "summary_et": "DFM leidis 2 hoiatust. Detail on prinditav, ...",
  "counts": {"critical": 0, "warning": 2, "info": 1},
  "issues": [
    {"severity": "warning", "rule": "load_vs_wall",
     "message_et": "10kg koormuse jaoks soovitame seinapaksust ≥ 7mm...",
     "affected_param": "wall_thickness", "suggested_value": 7}
  ]
}
```

**Score:** `max(1, min(10, 10 − Σ(severity_weight × count)))`. severity_weight:
critical=3.0, warning=1.2, info=0.4.

**`POST /api/ai/fix`** kombineerib reeglipõhise raporti LLM-iga — LLM annab
inimmõtlevas keeles selgituse ja kontekstuaalse remediation-plaani reegli-
issue'de peal. Kui reegel ei leia ühtegi issue'd, skipime LLM-kutse ja
säästame raha.

## RAG-lite template-soovitaja

Uus kasutaja kirjutab "vaja seinale konksu 5kg kotti" — enne Claude-kutset
küsime `TemplateRagService.suggestTemplates(prompt)`, mis teeb Postgresi
`pg_trgm similarity()` otsingu `prompt_history` tabeli peal:

```sql
SELECT *, similarity(prompt_et, :q) AS sim
FROM prompt_history
WHERE prompt_et % :q  -- trigram match
ORDER BY (CASE WHEN downloaded THEN 2 ELSE 1 END) * similarity(prompt_et, :q) DESC
LIMIT :limit
```

`downloaded=true` kirjed (need kus kasutaja tegelikult STL'i alla laadis)
saavad 2× kaalu — õpime tõestatult eduka matching'i pealt.

Top-3 template'it läheb Claude'ile prompti lisasse hint'ina ("ajaloost
8/10 sama fraasi kasutajatest valisid `hook`"). Claude võib seda järgida
või vastu valida — aga konvergeerumine on kiirem.

**Miks mitte päris pgvector + embeddings?** Praegune korpus on alla 10k
rida eesti keeles lühikeste fraasidega — pg_trgm annab sellel mastaabil
samaväärselt head tulemused ilma extra infra'ta. Kui korpus ületab 100k
või lisandub mitu keelt, migreerume bge-m3 embedding'ute peale.

## Andmebaasi skeem (V5)

```
ai_reviews           — multi-agent council ajalugu (JSONB)
prompt_history       — RAG korpus + tsvector/pg_trgm indeksid
design_iterations    — generative loop'i sammude snapshot
```

Migration: `backend/src/main/resources/db/migration/V5__ai_superpowers.sql`.

## API endpoints

| Endpoint | Kirjeldus |
|---|---|
| `POST /api/ai/review-council` | 4-agent + synthesizer (vastus ~4s) |
| `POST /api/ai/iterate` | SSE stream generative loop |
| `POST /api/ai/dfm` | Reeglipõhine DFM audit (< 100ms) |
| `POST /api/ai/fix` | DFM + LLM kombo remediation |

## Frontend

`/ai-studio` route — `AiStudioComponent` (standalone).

Template + params JSON editor + prompt_et + target_score → kolm nuppu:
1. 🏛 **Kutsu nõukogu** — council UI, 4 agendi kaardid värvidega
2. 🔧 **DFM-audit** — issued heat-map'iga
3. 🔁 **Itereeri täiuseni** — timeline SSE event'idega

## Testid

- `worker/test_dfm.py` — iga DFM reegli unit-testid + endpoint integration
- `backend/src/test/java/ee/krerte/cad/ai/AgentPersonaTest.java` — kaalude summa = 1.0
- `backend/src/test/java/ee/krerte/cad/ai/GenerativeLoopServiceTest.java` —
  loop'i terminatsiooni-tingimused (target/max_iter/regression/clamp)

## Proov

```bash
# DFM audit
curl -s -X POST http://localhost:8080/api/ai/dfm \
  -H 'Content-Type: application/json' \
  -d '{"template":"shelf_bracket","params":{"wall_thickness":2,"load_kg":10,"arm_length":180,"pipe_diameter":30}}' | jq

# Multi-agent council (vajab ANTHROPIC_API_KEY)
curl -s -X POST http://localhost:8080/api/ai/review-council \
  -H 'Content-Type: application/json' \
  -d '{"spec":{"template":"shelf_bracket","params":{"wall_thickness":3,"load_kg":10}},"prompt_et":"tugev klamber raskusele"}' | jq

# Generative loop — SSE stream
curl -N -X POST http://localhost:8080/api/ai/iterate \
  -H 'Content-Type: application/json' \
  -d '{"spec":{"template":"shelf_bracket","params":{"wall_thickness":2}},"prompt_et":"tugev 10kg","target_score":8.5}'
```
