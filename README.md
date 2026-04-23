# AI-CAD

Eesti AI-põhine CAD-teenus. Kasutaja kirjeldab detaili eesti keeles → Claude API
parsib selle parameetriliseks spetsifikatsiooniks → CadQuery genereerib STL-i →
kasutaja laeb alla ja saadab valitud 3D-print-teenusele.

[![CI](https://github.com/KrErte/CAD/actions/workflows/ci.yml/badge.svg)](https://github.com/KrErte/CAD/actions/workflows/ci.yml)
[![Release](https://github.com/KrErte/CAD/actions/workflows/release.yml/badge.svg)](https://github.com/KrErte/CAD/actions/workflows/release.yml)

> **🏭 UUS: [PrintFlow MES](./docs/PRINTFLOW.md)** — AI-CAD on nüüd kaks tööriista
> ühes. Olemasoleva *home* disaini poole on nüüd ka **`/#/factory`** —
> täielik MES (Manufacturing Execution System) 3D-printimise teenusebüroodele:
> Instant Quote Engine, DFM analüüs, printerifarm real-time SSE stream'iga,
> tööjärjekord, materjali-inventar, RFQ postkast, webhook integratsioonid.
> [Loe lisa →](./docs/PRINTFLOW.md)

## Mis on praegu olemas

**6 parameetrilist template'it** (kõigil JSON-skeemiga min/max validatsioon):

| Template | Mille jaoks |
|---|---|
| `shelf_bracket` | L-kujuline torule kinnituv riiuliklamber |
| `hook` | Seinakonks koormusele X kg |
| `box` | Kandiline karp seinapaksusega |
| `adapter` | Toruline läbimõõdu-adapter |
| `cable_clamp` | Mitme kaabli seinahoidik |
| `tag` | Lapik silt augukesega |

**Fallback**: kui ükski template ei sobi, kasutame [Meshy.ai](https://meshy.ai)
text-to-3D API-t (vabavormiline mesh, GLB).

**Slicer preview**: peale STL-i genereerimist pakub rakendus `POST /api/preview`
kaudu täpsed numbrid (prindiaeg, filamendi mass, hind €) — sidecar PrusaSlicer
CLI kaudu. Kui slicer pole saadaval, langetakse tagasi heuristilisele
hinnangule (volume × PLA tihedus).

**AI Design Review** (`POST /api/review`): peale STL-i genereerimist saab
küsida Claude-vision-põhist ülevaadet. Backend saadab mudelile kolm asja —
kasutaja originaalse eestikeelse soovi, resolveeritud spec'i ja three.js
eelvaate PNG-pildi — ja mudel vastab struktureeritud tööriista-kutsega:

```json
{
  "score": 7,
  "verdict_et": "Kindel ja prinditav, aga ülakinnituse kõrgus võiks olla 2mm kõrgem.",
  "strengths": ["Seinapaksus sobib 5kg koormusele", "..."],
  "weaknesses": ["Ankurdusaugu tolerants on tihe"],
  "suggestions": [
    { "label_et": "Suurenda seinapaksust 5mm peale",
      "rationale_et": "praegune 3mm võib 5kg all väänduma hakata",
      "param": "wall_thickness", "new_value": 5 }
  ]
}
```

Frontendis on iga numbriline soovitus klikitav — "Rakenda" nupp patchib
spec'i, clampib template skeemi min/max piiresse ja genereerib STL-i uuesti.
Self-critiquing generative CAD.

## Stack

- **Backend**: Spring Boot 3 / Java 21 — REST API + Claude/Meshy/Slicer proxy
- **Worker**: Python 3.11 + FastAPI + CadQuery 2.4 (OCP / OpenCascade)
- **Slicer**: Ubuntu 22.04 + PrusaSlicer CLI + FastAPI sidecar
- **Frontend**: Angular 18 (standalone + signals) + three.js
- **Pakettimine**: Docker, Docker Compose, Helm chart, k8s manifestid
- **CI/CD**: GitHub Actions → ghcr.io

Detailne arhitektuur: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
OpenAPI spec: [`docs/openapi.yaml`](docs/openapi.yaml).

## Lokaalne käivitamine

```bash
cp .env.example .env       # sisesta ANTHROPIC_API_KEY (ja soovi korral MESHY_API_KEY)
docker compose up --build
```

- Frontend: <http://localhost:4200>
- Backend:  <http://localhost:8080/api/templates>
- Worker:   <http://localhost:8000/health>
- Slicer:   <http://localhost:8100/health>

## Testid

```bash
cd worker && pytest -v          # template smoke-tests
cd slicer && pytest -v          # gcode-header parser + mocked /slice
cd backend && ./gradlew test    # backend unit-tests
cd frontend && npm test         # Angular tests
```

## Deploy

### Helm (soovituslik)

```bash
helm install ai-cad ./helm/ai-cad \
  --namespace ai-cad --create-namespace \
  --set-string secrets.anthropicApiKey=$ANTHROPIC_API_KEY \
  --set-string secrets.meshyApiKey=$MESHY_API_KEY \
  --set ingress.host=cad.minu-domeen.ee
```

### Plain k8s manifestid

```bash
kubectl apply -f k8s/namespace.yaml
cp k8s/secret.example.yaml k8s/secret.yaml   # täida väärtused
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/
```

## Repo struktuur

```
backend/   Spring Boot 3 (ClaudeClient, WorkerClient, SlicerClient, MeshyClient)
worker/    FastAPI + CadQuery, üks @register decorator iga template kohta
slicer/    FastAPI + PrusaSlicer CLI sidecar (print-time + filament + hind)
frontend/  Angular 18 + three.js viewer
k8s/       Plain manifestid (Deployment + Service + Ingress + Secret)
helm/      Helm chart sama jaoks
docs/      ARCHITECTURE.md, openapi.yaml
.github/   CI (per-component) + Release (multi-arch ghcr.io push)
```

## Pricing

Three audience segments — Makers, Print Bureaus, Developers. See [docs/PRICING.md](./docs/PRICING.md) for full details.

- **Demo**: 2 generations/day, no auth required (`POST /api/demo/generate`)
- **Maker** (Free): 100 gen/mo, 30 reviews, 10 meshy
- **Creator** (29.99 €/mo): 500 gen/mo, Darwin CAD, STEP export
- **Bureau plans**: Starter → Studio → Factory → Enterprise
- **Developer plans**: Trial (14d) → Growth → Business (API access)

Public pricing endpoint: `GET /api/pricing/plans`

## Roadmap

- LLM-juhitud uute template'ide loomine (review queue, mitte auto-merge)
- Mitmeosalised assembly'd + poltide arvu solver
- ~~Print-time/filament-mass eelvaade (PrusaSlicer CLI sidecar)~~ ✅ olemas
- Kasutajakontod, tellimuste ajalugu, partnerite API (3DKoda, 3DPrinditud)
