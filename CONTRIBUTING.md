# Kuidas panustada

Tänan huvi eest! See dokument aitab sul tööga alustada — kuidas repo'd
seadistada, mis on koodi-standardid ja kuidas PR submit'ida.

## Quick start

```bash
git clone https://github.com/olen-krerte/CAD.git
cd CAD

# ── Pre-commit hookid (Python) ──
pip install pre-commit
pre-commit install
pre-commit install --hook-type commit-msg   # Conventional Commits gate

# ── Backend (Java 21) ──
cd backend
gradle build
gradle bootRun
# API: http://localhost:8080

# ── Frontend (Node 20+) ──
cd frontend
npm install --legacy-peer-deps
npm start
# UI: http://localhost:4200

# ── Worker + Slicer (Python 3.11+) ──
cd worker && pip install -r requirements.txt && uvicorn main:app --port 8000
cd slicer && pip install -r requirements.txt && uvicorn main:app --port 8100

# ── Või kõik korraga Docker'ist ──
docker compose up --build
# + Redis/MinIO:
docker compose -f docker-compose.yml -f docker-compose.infra.yml up --build
# + observability stack:
docker compose -f docker-compose.yml -f docker-compose.observability.yml up
```

## Branch & commit konventsioon

**Branch-nimi**:
- `feat/<teema>` — uus funktsionaalsus
- `fix/<issue-id>-<lühikirjeldus>` — bugfix
- `chore/<teema>` — tooling, CI, infra
- `docs/<teema>` — ainult dokumentatsioon
- `refactor/<teema>` — ei muuda käitumist

**Commit message** — [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <lühike pealkiri>

<põhjalikum keha — miks, mitte mis>

BREAKING CHANGE: <kirjeldus>   # kui on
```

**Type'id**: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`, `ci`, `build`, `revert`

Näide:
```
feat(printflow): quote-engine honors multi-material pricing

Previously priced everything at base material rate. Now it checks
QuoteLine.material and applies per-kg multiplier from MaterialRepo.
Tests: +4 pricing cases. CI green.

Fixes #142
```

## Code style

**Backend** (Java): Google Java Format AOSP (4-space). Spotless automaatselt.
```bash
cd backend && gradle spotlessApply
```

**Frontend** (TypeScript): ESLint + Prettier.
```bash
cd frontend && npx eslint --fix && npx prettier --write "src/**/*.{ts,html,scss}"
```

**Python** (worker/slicer): ruff (format + lint).
```bash
ruff format worker slicer && ruff check --fix worker slicer
```

Pre-commit hook teeb need automaatselt. Kui mingi format-reegel sulle
ei sobi, ära disable'i seda lokaalselt — ava PR `.pre-commit-config.yaml`
muudatusega, siis arutame.

## Testid

**Iga PR peab läbima**:

| Test | Käsk | CI gate |
| ---- | ---- | ------- |
| Backend unit | `cd backend && gradle test` | ✅ peab passima |
| Backend coverage | `gradle jacocoTestCoverageVerification` | 60% line min |
| Backend integration | `gradle test --tests "*IT"` | ✅ peab passima |
| Frontend unit | `npm test -- --watch=false` | ✅ peab passima |
| Frontend build | `npm run build` | ✅ peab passima |
| Worker/slicer | `pytest -v --cov=.` | ✅ peab passima |
| E2E (smoke) | `npm run e2e` | main push peal |

Vt `docs/TESTING.md` põhjalikumat strateegiat.

## ADRs (Architecture Decision Records)

Suuremad arhitektuuri-otsused dokumenteeritakse kui ADR-d — `docs/adr/NNNN-pealkiri.md`.
Eesmärk: kuu aja pärast võib uus liige lugeda ja saada aru, miks X, mitte Y
valiti.

Template: `docs/adr/000-template.md`. Kopeeri, täida, sea status (`proposed`,
`accepted`, `deprecated`).

## PR workflow

1. **Loo branch** põhinedes `main`'il: `git checkout -b feat/minu-teema main`
2. **Commit'i** väikeste tükkidena — iga commit peaks tool'id rohelises hoidma
3. **Push** ja loo PR. Täida template (koosneb kontroll-lehe-st).
4. **CI** peab roheline olema. Fail'inud CI = PR'i ei review'i.
5. **Review**: @olen-krerte vaatab üle. Võib küsida muudatusi.
6. **Merge**: squash-merge main'i peale. Branch kustutatakse.

## Mida MITTE teha

- ❌ Commit otse `main`'ile (branch protection rule ei luba)
- ❌ `git push --force` main'ile
- ❌ Secret'e (API key, parool) commit'i — Gitleaks blokkeerib, aga parem ei proovi
- ❌ Binaarseid fail'e > 500KB — need kuuluvad S3/MinIO, mitte Git'i
- ❌ `node_modules/`, `build/`, `.env` commit'i — vt `.gitignore`
- ❌ Disable pre-commit hookid lokaalselt ilma PR-ita

## Turva-probleemid

Palun raporteeri privaatselt, mitte avalikult issue'i vormi kaudu — vt
`SECURITY.md`.

## Küsimused

- **Üldine**: ava Discussion või issue
- **Bug**: kasuta Bug template'i
- **Feature**: kasuta Feature template'i
- **Otsekontakt**: olen@krerte.ee
