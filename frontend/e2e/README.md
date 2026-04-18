# E2E testid — TehisAI CAD

Playwright smoke + põhivoog testid. Eesmärk: iga deploy enne kui kood läheb
prodi, peavad need testid rohelised olema.

## Paigaldus

```bash
cd frontend
npm install                 # kui veel tegemata
npm run e2e:install         # lae alla Chromium (1x)
```

## Jooksutamine

```bash
# Kõik testid, headless
npm run e2e

# Interaktiivne UI — debugimiseks
npm run e2e:ui

# Tavalises brauseris (näed mis toimub)
npm run e2e:headed

# Ainult konkreetne test-fail
npx playwright test landing.spec.ts

# Ainult ühe testi nimi (substring match)
npx playwright test -g "hero laadub"
```

### Eeldused

- Frontend jookseb `http://localhost:4200` (skript käivitab automaatselt `npm start`)
- Backend jookseb `http://localhost:8080` (api-smoke jaoks: `docker compose up` või `./gradlew bootRun`)
- Kui soovid muud baseURL'i: `E2E_BASE_URL=https://staging.tehisaicad.ee npm run e2e`
- API smoke'iks: `API_BASE_URL=https://staging-api.tehisaicad.ee npm run e2e`

## CI integratsioon

```yaml
# .github/workflows/e2e.yml näidis
- name: Install deps
  run: cd frontend && npm ci

- name: Install Playwright
  run: cd frontend && npx playwright install --with-deps chromium

- name: Start backend + worker
  run: docker compose up -d && sleep 30

- name: Start frontend
  run: cd frontend && npm start &
  env: { CI: 'true' }

- name: Wait for servers
  run: npx wait-on http://localhost:4200 http://localhost:8080/api/health

- name: Run E2E
  run: cd frontend && npm run e2e
  env: { CI: 'true' }

- uses: actions/upload-artifact@v4
  if: always()
  with:
    name: playwright-report
    path: frontend/playwright-report/
```

CI-mode: `retries=2`, `workers=1`, `webServer` ei käivitu automaatselt
(eeldame, et backend+frontend on juba käivitatud).

## Testi kategooriad

| Fail | Mis katab | Sõltuvus |
|------|-----------|----------|
| `landing.spec.ts` | Hero, nav, use-case'id, pricing 4-tier, kuu/aasta lüliti, FAQ, SEO meta | ainult frontend |
| `use-case.spec.ts` | Use-case kaartide klikk → prompt populatsioon | ainult frontend |
| `api-smoke.spec.ts` | `/api/health`, `/api/templates`, auth-gated endpoint'id | frontend + backend |

Kokku: 14 testi, tüüpiline jooks ~30s lokaalselt.

## Mida testid EI kata (järgmised sammud)

- **Autenditud voog** — Google Sign-In mockimine, Firebase test-user — vajab
  backend'is `AUTH_MOCK_TOKEN` endpointi.
- **Darwin CAD täielik evolve tsükkel** — `POST /api/evolve/seed` → valik →
  `POST /api/evolve/cross` → 6 uut varianti. Vajab CadQuery worker'i mock'i või
  päris töötavat worker'i pipeline'i.
- **Freeform SVG genereerimine** — kontrolli, et editor'is sisestatud
  CadQuery kood tagastab STL/STEP/SVG ja 3D vaatur renderdab.
- **Visuaalne regressioon** — screenshot-diff (`toHaveScreenshot`) hero
  animatsioonist + pricing kaartidest — ettevaatust fontide ja GPU erinevustega CI-s.
- **Mobile** — lisa `devices['iPhone 13']` ja `devices['Pixel 7']` projektid kui
  mobiilne liiklus > 20% (vaata Plausible/PostHog).
- **Load test** — see pole Playwrighti rida, aga k6/Grafana peaks tegema
  `/api/generate` jaoks 100 rpm stressi.

## Laiendamine

Uue testi lisamiseks loo `e2e/*.spec.ts`, impordi `test, expect` ja kasuta
`page.goto('/')` (baseURL lisatakse ette).

```typescript
import { test, expect } from '@playwright/test';

test('minu uus test', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('h1')).toBeVisible();
});
```

## Debugimine

```bash
# Halb test? Ava trace:
npx playwright show-trace test-results/.../trace.zip

# HTML report
npx playwright show-report
```
