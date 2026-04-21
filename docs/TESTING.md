# Testing strategy

Ülevaade, kuidas me siin kolmandal-korrusel kindlustame, et kood töötab.

## Test püramiid

```
           ┌─────────────┐
           │  E2E (~10)  │   Playwright — prompt → STL download
           └─────────────┘
         ┌───────────────────┐
         │  Integration (~30) │  Testcontainers + WireMock
         └───────────────────┘
       ┌───────────────────────────┐
       │    Unit tests (~200)      │  JUnit 5, pytest, Karma
       └───────────────────────────┘

    Load tests jooksevad eraldi, staging'i vastu.
```

## Backend (Spring Boot)

### Unit

- **Asukoht**: `backend/src/test/java/ee/krerte/cad/**/*Test.java`
- **Mis**: Pure-Java service'id ilma Spring context'ita — kõige kiiremad
- **Näited**: `PricingServiceTest`, `AgentPersonaTest`, `GenerativeLoopServiceTest`

### Integration (`*IT.java`)

- **Base klass**: `ee.krerte.cad.integration.AbstractPostgresIntegrationTest`
- **Mis**: Päris Postgres 16 läbi Testcontainers — validate, et JPA mapping
  ja Flyway migratsioonid ei lekita prod'i rikkis
- **Reuse**: container jääb elus JVM'i vahel (`.withReuse(true)`) — 20x kiirem
  iteratsioon lokaalselt

### Contract (`backend/src/test/java/.../contract/`)

- **Mis**: WireMock stubid Anthropic / Meshy / Stripe'i vastu — kontrollime
  et meie request'id on kontrakti-compliant ja response-parser ei katke
- **Miks mitte päris API'd**: ei kuluta krediiti, deterministlik, ei sõltu
  välisest teenusest CI'd jooksutades

### Coverage

- **Gate**: Jacoco 60% klasside pealt (build.gradle verificationTask)
- **Raport**: `backend/build/reports/jacoco/test/html/index.html`
- **Upload**: Codecov nii PR'ide kui main branch'i peal

```bash
cd backend
gradle test jacocoTestReport                # kõik testid
gradle test --tests "*IT"                   # ainult integration
gradle test --tests "*ContractTest"         # ainult contract
```

## Frontend (Angular)

- **Unit**: Karma + Jasmine, `ng test --code-coverage` → lcov Codecov'i
- **Lint**: `npm run lint` (ESLint + TypeScript strict)
- **E2E**: Playwright 4 browsers (Chromium baseline, WebKit + Firefox optional)

```bash
cd frontend
npm test                          # unit
npm run e2e                       # Playwright headless
npm run e2e:ui                    # interaktiivne debug
```

## Worker / Slicer (FastAPI)

- **pytest** + **pytest-cov** — coverage.xml Codecov'i
- **Smoke testid** ilma PrusaSlicer install'ita — subprocess on monkey-patched

```bash
cd worker && pytest -v --cov=.
cd slicer && pytest -v --cov=.
```

## Load tests (k6)

Vt `load-tests/README.md`. Staging'i vastu, mitte prod'i.

## CI

Kõik testid jooksevad `.github/workflows/ci.yml` peal iga PR'i ja main push'iga.
Load test on eraldi `.github/workflows/load-test.yml` — manuaalne või
iga-pühapäevane cron.

## Mis tulemas

- [ ] Pact consumer-driven contracts (kui frontend-backend kontrakt hakkab kõikuma)
- [ ] Mutation testing (PIT) backend'ile — kas meie testid PÄRIS leiavad vigu
- [ ] Chaos testing staging'is — kill worker, vaata kas backend recoverib
