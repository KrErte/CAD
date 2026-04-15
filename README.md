# AI-CAD

Eesti AI-põhine CAD-teenus. Kasutaja kirjeldab detaili eesti keeles → Claude API
parsib selle parameetriliseks spetsifikatsiooniks → CadQuery genereerib STL-i →
kasutaja laeb alla ja saadab valitud 3D-print-teenusele.

[![CI](https://github.com/KrErte/CAD/actions/workflows/ci.yml/badge.svg)](https://github.com/KrErte/CAD/actions/workflows/ci.yml)
[![Release](https://github.com/KrErte/CAD/actions/workflows/release.yml/badge.svg)](https://github.com/KrErte/CAD/actions/workflows/release.yml)

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

## Stack

- **Backend**: Spring Boot 3 / Java 21 — REST API + Claude/Meshy proxy
- **Worker**: Python 3.11 + FastAPI + CadQuery 2.4 (OCP / OpenCascade)
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

## Testid

```bash
cd worker && pytest -v          # template smoke-tests
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
backend/   Spring Boot 3 (ClaudeClient, WorkerClient, MeshyClient)
worker/    FastAPI + CadQuery, üks @register decorator iga template kohta
frontend/  Angular 18 + three.js viewer
k8s/       Plain manifestid (Deployment + Service + Ingress + Secret)
helm/      Helm chart sama jaoks
docs/      ARCHITECTURE.md, openapi.yaml
.github/   CI (per-component) + Release (multi-arch ghcr.io push)
```

## Roadmap

- LLM-juhitud uute template'ide loomine (review queue, mitte auto-merge)
- Mitmeosalised assembly'd + poltide arvu solver
- Print-time/filament-mass eelvaade (PrusaSlicer CLI sidecar)
- Kasutajakontod, tellimuste ajalugu, partnerite API (3DKoda, 3DPrinditud)
