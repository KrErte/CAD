# AI-CAD architecture

## High-level

```
 ┌──────────┐    POST /api/spec      ┌─────────────┐   Anthropic Messages
 │ Angular  │ ─────────────────────► │  Backend    │ ─────────────────────► Claude API
 │  18 SPA  │   POST /api/generate   │ (Spring 3)  │
 │  three.js│ ◄───── STL bytes ───── │             │ ─── HTTP /generate ──► CadQuery worker
 └──────────┘                        │             │ ─── HTTP v2/text-3d ─► Meshy.ai (fallback)
                                     └─────────────┘
```

Three independently deployable services. The backend is the only one that talks
to external paid APIs, so secrets stay there.

## Components

### Frontend (`/frontend`)
- Angular 18 standalone component, signals, no NgModules.
- three.js renders the returned STL in the browser; nothing leaves the user's
  machine after generation except the prompt.
- Range sliders are bounded by the JSON schema served from `/api/templates`,
  which prevents users sending out-of-range params to the worker.

### Backend (`/backend`)
- Spring Boot 3 / Java 21, single fat jar.
- `ClaudeClient` — sends the user's Estonian prompt + the worker template
  catalog + a strict system prompt. Response must be JSON of shape
  `{template, params, summary_et}` or `{error}`. Validated with Jackson.
- `WorkerClient` — proxies `/templates` and `/generate` to the worker over the
  cluster network.
- `MeshyClient` — fallback for free-form requests when no template fits.
  Optional; disabled when `MESHY_API_KEY` is empty.
- Stateless. Scales horizontally.

### Worker (`/worker`)
- Python 3.11 + FastAPI + CadQuery 2.4 (OCP / OpenCascade).
- Each parametric template is registered via `@register(name, schema)`. The
  schema doubles as input validation and the frontend's UI source of truth.
- Generates STL into a temp file, returns it as `application/sla` bytes.
- CPU-bound; OCP allocates a lot. We target 1 vCPU / 1 GiB per replica.

## Data flow

1. User types Estonian prompt → `POST /api/spec`.
2. Backend asks Claude to map prompt → `(template, params)`.
3. Frontend renders sliders bounded by schema. User can tweak.
4. User clicks Generate → `POST /api/generate` → backend forwards to worker.
5. Worker runs CadQuery, exports STL, returns bytes.
6. Frontend renders STL via three.js and offers download.

If Claude returns `error: "no_match"` the UI offers the Meshy fallback, which
posts the raw prompt to `/api/meshy` and gets back a GLB URL.

## Why this split

- **Worker isolated** because CadQuery + OCP needs a conda-style stack
  (`mambaorg/micromamba` base) that we don't want infecting the backend image.
- **Backend in Java** because the existing org runs Spring; observability,
  config, and CI tooling are reused for free.
- **Frontend separate** because three.js + CAD viewing wants a real client app
  rather than server-side rendering.

## Deployment

- Local: `docker compose up`. See main README.
- Cluster: `helm install ai-cad ./helm/ai-cad`. Plain manifests under `/k8s`
  for clusters without Helm.
- Images are built and pushed to `ghcr.io/krerte/cad-{backend,worker,frontend}`
  by `.github/workflows/release.yml` on every push to `main` and on tags.

## Security notes

- API keys are never sent to the browser; the frontend only knows about
  `/api/*` URLs.
- Worker has no outbound internet access by design — it just renders geometry.
- Templates' parameter schemas hard-bound user input before it reaches CadQuery.
- Backend logs prompts at DEBUG only; production should keep INFO.

## Roadmap

- LLM-driven authoring of new templates (review queue, not auto-merge).
- Multi-part assemblies + bolt count solver.
- Slicing preview (estimated print time / filament weight) using PrusaSlicer
  CLI in a sidecar.
