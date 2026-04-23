# Prompt Claude Code'ile — hinnastamise implementatsioon

Kopeeri kogu alljärgnev osa "---" joonte vahelt Claude Code'i.

---

# Ülesanne: lisa hinnastamine AI-CAD projektile

Meie toode on **kaks eri toodet ühes koodibaasis**:
1. **AI-CAD** — tarbija tööriist (`/#/home`): eestikeelne/ingliskeelne kirjeldus → parametric CAD → STL → partnerite hinnavõrdlus
2. **PrintFlow MES** — B2B SaaS 3D-print-büroodele (`/#/factory`): quote engine, DFM, printerifarm dashboard, RFQ postkast, webhook'id
3. **Developer API** — kolmas tuluallikas: STL genereerimise API välistele arendajatele

Praegu on hinnastamine täielikult puudu. See kaob usaldust müügikõnedes, ja cold-email'idele print-büroodele (kus lubame "3 kuud tasuta") ei ole selge mis kuu 4 maksab.

**Tähtis põhimõte**: meil ei ole "Free forever" plaani üheski tooteosas. Igal plaanil on **14-päevane tasuta trial** (krediitkaarti ei küsi, ainult e-mail). Tarbija pool saab avaliku krediitkaardi-vaba demo-režiimi landing page'il (piiratud, anonüümne, ei ole plaan).

## Hinnastamine (fikseeritud — ära muuda numbreid)

### Makers (tarbijad) — AI-CAD

Anonüümne **demo-režiim** landing page'il: 2 generatsiooni päevas per IP, ainult template'id, ilma AI Design Review'ta, ilma Meshy fallback'ita, ilma salvestamiseta. See **ei ole plaan** — see on müügikampaania. Iga demo-generatsiooni järel näidatakse "Sign up for 14-day trial" CTA-d.

| Plaan | Hind | Generatsioonid | Design Reviews | Meshy fallback | Muud |
|---|---|---|---|---|---|
| **Maker** | 9 € / kuu | 100 / kuu | 30 / kuu | 10 / kuu | Privaatne galerii, bulk export (ZIP) |
| **Creator** | 29 € / kuu | 500 / kuu | 150 / kuu | 50 / kuu | API 200 kõnet kuus, ilma watermark'ita STL, prioriteet-queue |

Mõlemal plaanil on **14-päevane tasuta trial** ilma krediitkaardita. Trial'i ajal saab kasutada Maker-plaani limiite. Pärast trial'i peab valima plaani või account degrade'itakse "trial expired" olekusse (ei blokeerita, aga ei saa genereerida).

### Print Bureaus (B2B) — PrintFlow MES

| Plaan | Hind | Piirang | Omadused |
|---|---|---|---|
| **Starter** | 49 € / kuu | 50 tellimust / kuu, 1 printer | Instant Quote Engine, RFQ postkast, põhiline dashboard |
| **Studio** | 199 € / kuu | 500 tellimust / kuu, 10 printerit | + DFM audit, webhook'id, SSE printerifarm stream, materjali-inventar |
| **Factory** | 499 € / kuu | Piiramatu | + SSO (SAML/OIDC), SLA 99.9%, prioriteet-tugi, custom branding |
| **Enterprise** | Küsi pakkumist | Custom | White-label, self-hosted Helm chart, custom integratsioonid, DPA |

Iga plaan algab **14-päevase tasuta prooviga**, krediitkaarti ei küsita. Lisaks **3-kuu tasuta piloot-programm** esimestele 3 piloot-kliendile (kood `PILOT2026`).

### Developers — API

| Plaan | Hind | Sisaldab |
|---|---|---|
| **Trial** | 0 € × 14 päeva | 500 STL-generatsiooni kokku trial'i kestel, rate-limit 60/min, matchib Growth'i featureid. Pärast 14 päeva peab plaani valima. |
| **Growth** | 49 € / kuu | 1 000 generatsiooni kuus, rate-limit 60/min, e-maili tugi |
| **Business** | 199 € / kuu | 5 000 generatsiooni kuus, rate-limit 300/min, prioriteet-tugi, webhook'id |
| **Pay-as-you-go** | 0.10 € / generatsioon | Lisaks plaani limiidile, maksad ainult mis üle läheb |

Kõik API-plaanid sisaldavad partnerite hinnavõrdluse API-t tasuta (me teenime affiliate-vahenduse).

## Ühiku-ökonoomika (MIKS neid numbreid)

Kontekst Claude Code'ile, et otsuseid mõista:

- Claude Haiku intent-parsing: ~0.002 € per kutse
- Claude Sonnet Design Review (vision + struktureeritud JSON): ~0.08 € per kutse
- Meshy.ai text-to-3D fallback: ~0.25 € per kutse

Maximum-kasutus Maker plaanilt maksab ~5.10 € tokenikulu (gross margin ~3.90 € ≈ 43%). Creator ~25.20 € (margin ~3.80 € ≈ 13%, napp aga positiivne). Demo-režiim maksab max 0.004 € päevas per IP.

See tähendab **mudelite valik on oluline**: intent-parser ja template-valik **peavad kasutama Claude Haiku** (`claude-haiku-4-5-20251001`), ainult Design Review tohib kasutada Sonnet'i (`claude-sonnet-4-6`). Ära sega neid.

## Mis vaja teha

### 1. Frontend: uus `/pricing` route

Loo `frontend/src/app/pricing/pricing.component.ts` (standalone, sama stiil nagu teised komponendid — signals, ei mingit NgModule'it).

Leht peab sisaldama:

- **Hero-sektsioon** — pealkiri "Simple pricing for makers, bureaus & developers", alampealkiri selgitab kolm sihtgruppi
- **Tab-lülitus** kolme sektsiooni vahel: "For Makers" / "For Print Bureaus" / "For Developers" (vaikimisi Makers aktiivne)
- **Pricing kaardid** igale plaanile — kaardil hind, plaani nimi, lühikirjeldus, piirangud, omaduste checklist, CTA-nupp ("Start 14-day trial" / "Contact sales")
- **"Try instantly" bänner** Makers tab'i kohale — "No signup? Try the [live demo](anchor) — 2 generations per day, no account needed"
- **Featured plaan esile tõstetud** — Makers sektsioonis "Creator" on featured, Bureaus'is "Studio", Developers'is "Growth"
- **FAQ accordion** all (6–8 küsimust: kas trial vajab krediitkaarti, kuidas vahetada plaani, tühistamine, käibemaks, dataomand, GDPR, partnerite hinnad, ülekäik-limiidid)
- **CTA-riba lõpus** — "Not sure which plan? Book a 15-min call" koos Calendly-placeholder-lingiga

CSS peab matchima olemasoleva `app.component.ts` stiiliga (kasuta samu CSS-muutujaid, glassmorphism-kaardid, `--accent`, `--bg`, jne). Vaata `styles.css` ja `app.component.ts` `styles: []` sektsiooni võrdluseks.

**Navigatsioon**: lisa "Pricing" link ülemisse navbar'i (leia see `app.component.ts`-ist), nähtav mõlemal route'il (`/home` ja `/factory`).

### 2. Frontend: demo-widget landing page'il

Loo uus komponent `frontend/src/app/demo/demo.component.ts`, mida embeddida `/home` route'i hero-sektsiooni alla:

- Prompt-tekstiväli (`"Describe a part, e.g. 'shelf bracket 5kg 4 holes'"`)
- "Generate" nupp
- three.js viewer genereeritud STL-i jaoks
- Pärast genereerimist: "Like it? [Start 14-day trial →]" CTA overlay + counter "1/2 demo generations today"
- Kui 2/2 täis: "Daily demo limit reached. [Start trial →]" ja generate-nupp disabled
- Kasuta uut endpoint'i `POST /api/demo/generate` (vt backend-osa), **mitte** olemasolevat `/api/generate`-d

Rate-limit peab olema tuvastatav kas cookie (lihtne) või IP-põhiselt (tagaselg backend'is), mitte pelgalt frontend'is — serveripoolne on ainus, mis päriselt töötab.

### 3. Backend: tier-enforcement + demo endpoint

Loo Spring Boot'i:

**Pricing struktuur**

- `backend/src/main/java/com/aicad/pricing/PricingPlan.java` — enum: `DEMO_ANONYMOUS`, `TRIAL_MAKER`, `MAKER`, `CREATOR`, `TRIAL_API`, `API_GROWTH`, `API_BUSINESS`, `TRIAL_BUREAU`, `BUREAU_STARTER`, `BUREAU_STUDIO`, `BUREAU_FACTORY`, `BUREAU_ENTERPRISE`
- `backend/src/main/java/com/aicad/pricing/PlanLimits.java` — record iga plaani piirangutega (`monthlyGenerations`, `monthlyReviews`, `monthlyMeshyFallback`, `rateLimitPerMin`, `maxPrinters`, `monthlyOrders`, `features: Set<Feature>`). `DEMO_ANONYMOUS` kasutab `dailyGenerations` asemel `monthly*`.
- `backend/src/main/java/com/aicad/pricing/PlanConfig.java` — `@Configuration` bean, mis tagastab `Map<PricingPlan, PlanLimits>` ülaltoodud numbrite põhjal
- `backend/src/main/java/com/aicad/pricing/UsageTrackingService.java` — skeleet meetoditega `incrementGeneration(userId, kind)`, `checkQuota(userId, plan, kind)`, `currentPeriodUsage(userId, kind)`. `kind` on `enum UsageKind { GENERATION, REVIEW, MESHY_FALLBACK }` — **eraldi kvoodid per kind**, mitte üks ühine bucket, kuna kulud on erinevad. Implementeeri Redis-põhiselt (kuu-võti: `usage:{userId}:{YYYY-MM}:{kind}` või päeva-võti demo jaoks: `usage:demo:{ipHash}:{YYYY-MM-DD}`). Kui Redis'it pole, langeta tagasi in-memory `ConcurrentHashMap`'ile (logi `WARN`).
- REST endpoint `GET /api/pricing/plans` — tagastab avaliku plaanide nimekirja JSON-ina (sama mis frontend'i pricing-lehel näidatakse), et mobile-klient ja marketing-site saaks sama tõe allikat kasutada

**Demo endpoint**

- `POST /api/demo/generate` — **eraldi controller** `DemoController.java`, ei vaja auth'i. Rate-limit IP per päev (2/päev, IP hash-itud SHA-256-ga + soolatud env'ist). Piirangud: ainult template'id (kui ClaudeClient tagastab "no template match" → 422 koos `{ "demo_limitation": "free_form_requires_trial" }`), ei tohi kutsuda `/api/review`, ei tohi kutsuda Meshy. Salvestuse TTL 1 tund Redis'is (anonüümse-sessiooni-id-ga), pärast seda STL kustub.
- `POST /api/generate`, `/api/review`, `/api/preview` jäävad authenticated-only'ks ja tagastavad `401` kui token puudub. **Mitte segada demot päris endpoint'idega.**

**Ülekäigu käitlus**

- Kui autentitud kasutaja ületab oma kvoodi: tagasta `402 Payment Required` vastusega `{ "quota_exceeded": "generations", "current": 100, "limit": 100, "upgrade_plan": "creator", "upgrade_url": "/#/pricing" }`. Frontend püüab ja kuvab modali.
- Kui demo-kasutaja (anon) tabab päevalimiiti: `429 Too Many Requests` vastusega `{ "demo_limit_reached": true, "upgrade_url": "/#/pricing" }`.

**Mudelite valik**

Loo `backend/src/main/java/com/aicad/claude/ClaudeClient.java` (või uuenda olemasolev):

- `parseIntent(String prompt)` — kasutab **Claude Haiku** (`claude-haiku-4-5-20251001`)
- `reviewDesign(Spec spec, byte[] pngPreview)` — kasutab **Claude Sonnet** (`claude-sonnet-4-6`)
- Kaks eraldi konfiguratsiooni-võtit: `anthropic.model.intent=claude-haiku-4-5-20251001` ja `anthropic.model.review=claude-sonnet-4-6` `application.yml`-is, et saaks overridee'da ilma koodimuutuseta.

### 4. README.md uuendus

Lisa `README.md`-sse uus sektsioon "## Hinnastamine" kohe enne "## Roadmap". Lühikokkuvõte kolmest sihtgrupist, link `/pricing` lehele ja link `docs/PRICING.md` faili (mille ka loo).

### 5. `docs/PRICING.md` — detailne dokumentatsioon

Detailne hinnaplaanide kirjeldus sama struktuuriga nagu ülaltoodud tabelid, aga iga plaani all paragraaf mida kasutaja saab, mida ei saa, näitenumbrid (nt "Kui sa genereerid 200 STL-i kuus, peaksid Creator plaani valima, sest Maker sellega ei kata"). Eraldi sektsioon "Miks me ei paku Free-igavesti plaani?" kus selgitame: trial katab try-before-you-buy, demo-režiim landing page'il katab unauthenticated try-it.

### 6. Testid

- Angular: üks spec-fail `pricing.component.spec.ts` mis kontrollib, et kõik 3 tab'i renderdavad õige arvu kaarte (Makers: 2, Bureaus: 4, Developers: 3 + pay-as-you-go chip)
- Angular: `demo.component.spec.ts` mis kontrollib päevalimiidi kuvamist ja disabled-nupu käitumist
- Backend: `PricingPlanTest.java` mis kontrollib, et `PlanLimits` tagastab õiged numbrid iga enum-väärtuse kohta
- Backend: `UsageTrackingServiceTest.java` mis kontrollib per-kind quota-check'i loogikat (fake clock + in-memory fallback), sh päeva-reset demo jaoks ja kuu-reset maksvate plaanide jaoks
- Backend: `DemoControllerTest.java` mis kontrollib IP-põhist rate-limiti (WebMvcTest + `X-Forwarded-For` header)

## Piirangud

- **Ära puuduta** olemasolevat `applySuggestion` loogikat ega AI Review komponente — neil on eraldi bugi mis on teises ülesandes.
- **Ära lisa** Stripe'i, Paddle'i ega ühtegi tegelikku makse-SDK-d. Ainult pricing display + tier-enforcement skelett + trial-state tracker.
- **Ära loo** täielikku user-authenticationi süsteemi. Kui vaja user-ID, kasuta olemasolevat session-ID-d (või placeholder JWT header'it). Trial-state salvesta Redis'isse võtmega `trial:{userId}:started_at` ja `trial:{userId}:plan` — expiration 14 päeva.
- Kogu UI-tekst inglise keeles pricing-lehel ja demo-widget'is (sihtgrupp rahvusvaheline), aga `docs/PRICING.md` eesti keeles.
- **Ära jäta** Free tier'it ühelegi tootesektsioonile. Kui kiusatus tekib "äkki lisan Free API'le" — ei. Trial katab selle vajaduse.

## Acceptance criteria

1. `npm run dev` (frontend) näitab `/pricing` route'i koos 3 tab'iga ja korrektsete kaartidega (Makers: 2 plaani, Bureaus: 4 plaani, Developers: 3 plaani + pay-as-you-go)
2. Landing page (`/home`) näitab demo-widget'it hero-sektsiooni all, mis suudab genereerida STL-i ilma auth'ita ja näitab "1/2 today" counterit
3. `GET /api/pricing/plans` tagastab kehtiva JSON-i kõigi plaanidega
4. `POST /api/demo/generate` ilma auth'ita töötab, 3. päringul samalt IP-lt tagastab `429`
5. `POST /api/generate` ilma auth'ita tagastab `401`
6. Intent-parsing kasutab Haiku mudelit (verifieeri log'idest või `@Value` inject'i kontrollist)
7. Design Review kasutab Sonnet mudelit
8. `./gradlew test` ja `npm test` lähevad rohelisena läbi
9. Navbar'is on nähtav "Pricing" link mõlemal `/home` ja `/factory` route'il
10. README.md ja `docs/PRICING.md` on uuendatud
11. Ei tohi olla konsooli-erroreid frontend'is ega backend'i käivitamisel

Tee kõik muudatused ühe PR-ina, commit-sõnumiga `feat(pricing): add 3-tier pricing + demo mode + Haiku/Sonnet split`. Kui midagi on ebaselge, küsi enne koodi kirjutamist, ära eelda.
