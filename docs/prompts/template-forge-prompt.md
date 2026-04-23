# Prompt Claude Code'ile — AI-põhine template-generatsiooni pipeline ("Template Forge")

Kopeeri kogu alljärgnev osa "---" joonte vahelt Claude Code'i.

---

# Ülesanne: ehita "Template Forge" — pipeline mis AI-abil laiendab meie template-library't Meshy-fallback log'idest

## Konteksts

Praegu, kui kasutaja prompt ei sobi ühelegi olemasolevale template'ile, langeme Meshy.ai text-to-3D API-le (0.25€ per kõne, suurim variabel-kulu meie stack'is). See tähendab, et **iga Meshy-fallback on samal ajal signaal**: see on template mis meil puudu ja mida turg soovib.

Template Forge on pipeline mis:
1. Logib kõik Meshy-fallback prompt'id
2. Klasterab sarnased promptid kokku
3. Kui klaster jõuab lävepunktini (5+ sarnast prompt'i), märgib selle "template-kandidaadiks"
4. Claude Sonnet genereerib kandidaadile CadQuery-koodi + JSON-skeemi
5. Admin'i web-UI lubab arendajal vaadata, testi-render'ida, approve/reject'ida
6. Approve'ituna fail liigub `worker/app/templates/` kausta ja registreerub automaatselt

**Tagab et 80% Meshy-kulu langeb 3 kuu jooksul** (oletus: 20% prompt'idest on klastritava ja uue template'iga kaetav).

## Mida teha

### 1. Postgres migration: `template_candidates` tabel

Failitee: `backend/src/main/resources/db/migration/V20260423__template_candidates.sql` (või järgmine Flyway-versioon, kontrolli olemasolevat järjekorda).

```sql
CREATE TABLE template_candidates (
    id BIGSERIAL PRIMARY KEY,
    prompt_hash VARCHAR(64) NOT NULL,
    normalized_prompt TEXT NOT NULL,
    occurrence_count INTEGER NOT NULL DEFAULT 1,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sample_prompts JSONB NOT NULL DEFAULT '[]'::jsonb,  -- kuni 10 originaalset kirjapildi
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
      -- pending | generating | generated | approved | rejected
    generated_code TEXT,        -- CadQuery Python kood, kui genereeritud
    generated_schema JSONB,     -- JSON-skeem, kui genereeritud
    generated_name VARCHAR(64), -- template-nimi snake_case, nt "gopro_hero_mount"
    generation_model VARCHAR(50),  -- "claude-sonnet-4-6"
    generation_cost_eur NUMERIC(10,4),
    reviewer_notes TEXT,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT uq_template_candidates_hash UNIQUE (prompt_hash)
);

CREATE INDEX idx_template_candidates_status ON template_candidates(status);
CREATE INDEX idx_template_candidates_occurrence ON template_candidates(occurrence_count DESC);
```

### 2. Logger: Meshy-fallback'i kutsumisel

Leia `backend/src/main/java/com/aicad/meshy/MeshyClient.java` (või kus Meshy-fallback käivitub). Enne Meshy API-kõnet kutsu uus teenus:

`TemplateCandidateLogger.log(String originalPrompt)` mis:
1. Normaliseerib prompti (kasutab olemasolevat `PromptNormalizer`-it cache-feature'ist)
2. Arvutab hashi
3. Teeb upsert'i `template_candidates` tabelisse:
   - Kui hash puudub: INSERT uus rida
   - Kui hash olemas: UPDATE `occurrence_count + 1`, `last_seen_at = NOW()`, `sample_prompts`-i lisa originaal (max 10)
4. Kõik see on **fire-and-forget async** (`@Async` või `CompletableFuture.runAsync`) — Meshy-kõne ei tohi oodata DB-kirjutust

Failitee: `backend/src/main/java/com/aicad/forge/TemplateCandidateLogger.java`

### 3. Kandidaadi-eskaleerija teenus

Failitee: `backend/src/main/java/com/aicad/forge/CandidateEscalationService.java`

Scheduled job (`@Scheduled(cron = "0 0 */6 * * *")` — iga 6 tundi), mis:
- Query'b kandidaadid, kus `status = 'pending'` ja `occurrence_count >= 5`
- Iga sellise kohta logib `INFO`: "Candidate {id} ({name}) reached threshold with {count} occurrences"
- Saadab admin'ile e-maili või Slack-teavituse **praegu jäta välja** — ainult log. Saad config-flagi lisada `forge.notifications.enabled=false` default'iga.

### 4. Code-generator: Claude Sonnet

Failitee: `backend/src/main/java/com/aicad/forge/TemplateCodeGenerator.java`

Meetod: `generateTemplate(long candidateId)` mis:
1. Laadib kandidaadi `template_candidates`-ist
2. Muudab staatuse `'generating'`-iks
3. Ehitab Claude Sonnet'ile prompti, mis sisaldab:
   - **Süsteemi-prompt**: "You are a CadQuery 2.4 expert generating a new parametric template. Output ONLY valid Python code and JSON schema, nothing else. Follow the exact pattern of existing templates."
   - **In-context näide**: kopeeri 2 olemasolevat template'it (nt `shelf_bracket.py` ja `hook.py`) tervenisti + nende JSON-skeemid. See on few-shot learning.
   - **Kandidaadi-info**: `sample_prompts` nimekiri (originaalid, mitte normaliseeritud)
   - **Nõuded**: template-nimi peab olema snake_case, peab kasutama `@register` decorator'it, peab sisaldama docstring'i eesti ja inglise keeles, iga param peab olema JSON-skeemis min/max-piiridega, default-väärtusega, unit'iga. **Ära kasuta** imports väljaspool `cadquery` ja `math`-i. Geomeetria peab olema **FDM-prinditav ilma tugedeta** (vältida overhang > 45°, minimum seinapaksus 1.5 mm).
   - **Väljund-formaat**: kaks koodiplokki — üks Python, teine JSON — märgitud `---PYTHON---` ja `---SCHEMA---` separaatoritega, et saaks lihtsalt parsida.

Struktuur Claude'i API-kutsele:

```java
var body = AnthropicMessage.builder()
    .model("claude-sonnet-4-6")  // või config-väärtus
    .maxTokens(4096)
    .system(SYSTEM_PROMPT)
    .addMessage(Role.USER, buildUserPrompt(candidate, existingExamples))
    .build();
```

Pärast vastust:
1. Parsib `---PYTHON---` ja `---SCHEMA---` plokid
2. Teeb basic-lint'i (kontrollib et `@register` on olemas, et `cadquery` on import'itud)
3. Salvestab `generated_code`, `generated_schema`, `generated_name`, `generation_cost_eur` (arvuta tokens × $15/1M output + input-share)
4. Muudab staatuse `'generated'`-iks

Kui parsimine ebaõnnestub või lint läbi ei lähe, muudab staatuse tagasi `'pending'`-iks ja logib `WARN`. Ei unusta uuesti proovida — admin peab käsitsi retry'tama.

### 5. Admin REST API

Uued endpoint'id (kõik `@PreAuthorize("hasRole('ADMIN')")` või lihtsa admin-key header'iga):

- `GET /api/admin/forge/candidates?status=pending` — tagastab kandidaatide nimekirja, sorteerituna `occurrence_count DESC`
- `GET /api/admin/forge/candidates/{id}` — üksikasjad koos kõigi sample_prompts'idega
- `POST /api/admin/forge/candidates/{id}/generate` — käivitab `TemplateCodeGenerator`-i, tagastab kohe `202 Accepted`, async'is genereeritakse (võid kasutada `@Async` või RabbitMQ-tüüpi queue'd, aga praegu piisab lihtsalt async-executor'ist)
- `POST /api/admin/forge/candidates/{id}/test-render` — võtab `generated_code` + default-parameetrid, saadab worker'isse `/api/internal/render-candidate` endpoint'ile (mida sa ka lood), tagastab STL kui baytes + preview PNG
- `POST /api/admin/forge/candidates/{id}/approve` — kopeerib `generated_code` faili `worker/app/templates/{generated_name}.py`, skeem'i `worker/app/schemas/{generated_name}.json`, muudab staatuse `'approved'`, teavitab worker'i et template-catalog'i reload'itada (nt REST-kõne `POST /api/internal/reload-templates`)
- `POST /api/admin/forge/candidates/{id}/reject` — muudab staatuse `'rejected'`, salvestab `reviewer_notes`
- `GET /api/admin/forge/stats` — meetrikud: kui palju kandidaate kokku, kui palju pending/approved/rejected, kokku säästetud Meshy-kutseid (hinnang: approved template × tema cluster-size × 0.25€)

### 6. Worker'i `POST /api/internal/render-candidate`

Uus endpoint `worker/app/main.py`-is:
- Accept'ib POST body JSON-i: `{ "code": "...", "params": {...} }`
- Kirjutab koodi ajutisse tmp-faili, import'ib dünaamiliselt (`importlib.util`), kutsub generate-funktsiooni
- Tagastab STL binary + render'itud PNG preview'i (use three.js headless või `trimesh` + matplotlib basic render)
- **Turvalisus**: see endpoint on ainult lokaalsest võrgust ligipääsetav, mitte avalikust (Docker-network-only). Config: `workers.candidate-render.allowed-hosts=backend`. Auth: internal HMAC'iga (jagatud secret backend ↔ worker).

### 7. Admin UI — Angular

Uus route `/admin/forge` komponendiga `forge-admin.component.ts`. Standalone, signals.

Sisu:
- **Header**: "Template Forge — AI-generated template candidates"
- **Stats-kaardid** ülal: `candidates pending: 23 | approved: 5 | rejected: 2 | estimated Meshy savings: €12.50`
- **Kandidaatide-tabel** allpool, sorteeritud occurrence_count järgi:
  | # | Sample prompt | Occurrences | First seen | Status | Actions |
  | 1 | "GoPro Hero 11 mount for 5-inch freestyle frame" | 8 | 2 days ago | pending | [Generate] |
  | 2 | "antenna holder TBS Unify Pro raspberry pi" | 6 | 5 days ago | generated | [Review] [Approve] [Reject] |
- **Review-modal** kui klikkad "Review":
  - Vasakul pool: generated Python kood (read-only monaco-editor või lihtne `<pre>`-blokk syntax-highlight'iga)
  - Paremal pool: JSON-skeem ja three.js viewer kus render'itakse test-STL default-parameetritega
  - Slider'id paremal all — saab reaalajas muuta parameetreid ja vaadata kuidas mudel käitub
  - All: "Approve" (roheline) ja "Reject + notes" (punane) nupud

Kasuta olemasolevat three.js integratsiooni `app.component.ts`-ist — tee sellest shared `StlViewerComponent` kui seda juba pole.

### 8. Metrics / Observability

Micrometer counter'id:
- `forge.candidates.logged` — iga Meshy-fallback logimine
- `forge.candidates.escalated` — kui kandidaat ületab 5-lävepunkti
- `forge.candidates.generated` — Claude'i edukas koodigeneratsioon
- `forge.candidates.approved` / `.rejected`
- `forge.generation.cost.eur` — summa kulutatud Sonnet'i-tokenitele

### 9. Testid

**Backend:**
- `TemplateCandidateLoggerTest.java` — mock'itud `PromptNormalizer`-iga, kontrolli et 1. kord INSERT, 2. kord UPDATE +1
- `CandidateEscalationServiceTest.java` — in-mem DB-ga, seeda 5 kirjet, kontrolli et log-message tuleb
- `TemplateCodeGeneratorTest.java` — mock'itud Anthropic-kliendiga, kontrolli et separaatorid parsitakse õigesti, kontrolli et invaliid-vastuse korral status läheb tagasi `pending`-iks
- `ForgeAdminControllerTest.java` — `@WebMvcTest`, kontrolli auth (403 ilma admin-võtmeta) + approve-endpoint'i mis teostab file-copy mock-FS'is

**Worker:**
- `test_render_candidate.py` — kontrolli et kehtiv CadQuery-kood render'ib STL-i, et vigane kood tagastab 400-error ilma crash'ita, et mitte-registreeritud funktsioon põhjustab selge error-message'i

**Frontend:**
- `forge-admin.component.spec.ts` — kontrolli et tabel kuvab õige arvu ridu, et "Generate" nupp kaob kandidaadilt kui staatus muutub

### 10. Dokumentatsioon

Uus fail `docs/TEMPLATE_FORGE.md` (2–3 lehekülge):
- Mis probleemi lahendab
- Flow-diagramm (promt → fallback → log → klaster → AI-gen → review → approve)
- Kuidas admin'is review'da
- Ohud: mis juhtub kui LLM genereerib pahatahtliku koodi (vastus: sandbox-worker + koodi-review)
- Metrics dashboard kuidas mõõta edu (Meshy-fallback-rate ajas)
- Kuidas käsitsi kandidaati lisada (INSERT SQL näide)

## Turvalisus — väga tähtis

LLM genereerib **Python-koodi**, mis läheb meie süsteemi. See on inject-risk. Nõuded:

1. **Koodi ei eksekuteerita automaatselt** — admin peab käsitsi review'ma ja klikkama Approve
2. **Worker'i `render-candidate` endpoint käivitab koodi isoleeritud protsessis** — `subprocess.run(["python", "-c", code], timeout=30, capture_output=True)`, mitte `exec()` pea-protsessis
3. **Import-allowlist**: enne render'it, AST-parsi kood, kontrolli et ainult `cadquery`, `math`, `numpy` import'itud. Keela `os`, `subprocess`, `socket`, `requests`, file-I/O väljaspool `/tmp`-d.
4. **Approve-nupp dedi-keelavad** v1-s kui kood sisaldab mis tahes `import` deklaratsiooni väljaspool allowlisti — admin näeb selget error-message'it ja peab käsitsi review'ma.

Kogu see turvakihistus on `backend/src/main/java/com/aicad/forge/CodeSafetyValidator.java`.

## Piirangud

- **Ära tee** GitHub-integratsiooni praegu (PR-ide automaatne avamine). See on v2.
- **Ära tee** embedding-põhist klastridamist — lihtne prompt-hash on piisav v1-ks.
- **Ära kirjuta** scheduled-job't mis automaatselt generate'iks — admin peab käsitsi klikkima "Generate". Automaatika tuleb pärast kui usaldus mudelile on ehitatud.
- **Ära muuda** olemasolevat Meshy-flow'd ära — ainult lisa logger kõrvale.
- **Ära jäta** turvanurki katmata — kui kahtlus, küsi mulle enne koodi kirjutamist.

## Acceptance criteria

1. Flyway migration jookseb läbi — `template_candidates` tabel olemas
2. Kui teed `/api/generate` päringu prompti'ga mis ei matchi ühegi template'iga, INSERT tekib `template_candidates`-isse (või UPDATE kui sama prompt tuleb uuesti)
3. `GET /api/admin/forge/candidates` tagastab kehtiva nimekirja
4. `POST /api/admin/forge/candidates/{id}/generate` (kandidaadilt `occurrence_count >= 5`) — pärast 10–30s on staatus `'generated'` ja `generated_code` pole null
5. Admin UI `/admin/forge` näitab kandidaate, "Review" modal kuvab koodi + testi-render'i
6. "Approve" nupp kopeerib faili `worker/app/templates/`-isse ja pärast worker'i reload'i `GET /api/templates` tagastab uue template'i
7. Turvalisus: kui proovid approve'ida koodi, mis sisaldab `import os`, süsteem keelab ja logib `WARN`
8. Kõik testid (`./gradlew test`, `pytest`, `npm test`) läbivad rohelisena
9. `docs/TEMPLATE_FORGE.md` on täidetud ja viidatud README-s

Commit-sõnum: `feat(forge): AI-powered template generation pipeline from Meshy fallback logs`

---

## Oodatud mõju 3 kuud pärast launchi

- 20–30 uut template'it automaatselt-tuvastatud turunõudluse põhjal
- Meshy-fallback-rate langenud 100% → ~30% (70% vähem kulu)
- Template-library kasvab exponentsiaalselt mitte arendaja-ajaga proportsionaalselt
- Uus müügiargument: "Our template library grows weekly based on what real users ask for"
