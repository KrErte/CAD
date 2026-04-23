# AI-CAD architecture

## High-level

```
 ┌──────────┐    POST /api/spec      ┌─────────────┐   Anthropic Messages
 │ Angular  │ ─────────────────────► │  Backend    │ ─────────────────────► Claude API
 │  18 SPA  │   POST /api/generate   │ (Spring 3)  │
 │  three.js│ ◄───── STL bytes ───── │             │ ─── HTTP /generate ──► CadQuery worker
 │          │   POST /api/preview    │             │ ─── HTTP /slice ─────► PrusaSlicer sidecar
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
- `SlicerClient` — proxies binary STL bytes to the PrusaSlicer sidecar for the
  precise print-time / filament-mass / cost preview. Optional; disabled when
  `SLICER_URL` is empty, in which case `/api/preview` gracefully degrades to
  the worker's volume-based heuristic.
- `MeshyClient` — fallback for free-form requests when no template fits.
  Optional; disabled when `MESHY_API_KEY` is empty.
- Stateless. Scales horizontally.

### Worker (`/worker`)
- Python 3.11 + FastAPI + CadQuery 2.4 (OCP / OpenCascade).
- Each parametric template is registered via `@register(name, schema)`. The
  schema doubles as input validation and the frontend's UI source of truth.
- Generates STL into a temp file, returns it as `application/sla` bytes.
- Also exposes a cheap `/metrics` endpoint (volume + bbox + heuristic
  print-time) used by the debounced slider-drag preview in the UI.
- CPU-bound; OCP allocates a lot. We target 1 vCPU / 1 GiB per replica.

### Slicer sidecar (`/slicer`)
- Ubuntu 22.04 + PrusaSlicer CLI + FastAPI. Wraps `prusa-slicer --export-gcode`
  inside `xvfb-run` (PrusaSlicer opens an X connection even for CLI flags).
- `POST /slice` accepts multipart STL bytes + optional preset / fill density /
  layer height overrides, shells out to the slicer, parses the resulting
  gcode header comments (`; estimated printing time`, `; filament used [g]`,
  `; filament cost`) and returns a small JSON.
- Ships two built-in presets: `pla_default` (0.2 mm / 20 % infill / PLA at 25
  €/kg) and `petg_default`. Profiles live under `slicer/profiles/*.ini`.
- CPU-bound per request; one replica is usually enough because sliced results
  should be cached by the caller.

## Data flow

1. User types Estonian prompt → `POST /api/spec`.
2. Backend asks Claude to map prompt → `(template, params)`.
3. Frontend renders sliders bounded by schema. User can tweak. Every tweak
   hits `POST /api/metrics` (debounced 250 ms) for an instant heuristic.
4. User clicks Generate → `POST /api/generate` → backend forwards to worker.
5. Worker runs CadQuery, exports STL, returns bytes.
6. Frontend renders STL via three.js, offers download, and fires
   `POST /api/preview` in parallel. The backend internally re-generates the
   STL (cheap — same worker call is idempotent) and forwards its bytes to the
   slicer sidecar, which returns precise print time + filament mass + EUR
   cost. Chips in the UI swap from "Hinnang" to "Täpne" when slicer data
   arrives (or stay on the heuristic if the slicer is down).

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
- Images are built and pushed to
  `ghcr.io/krerte/cad-{backend,worker,slicer,frontend}` by
  `.github/workflows/release.yml` on every push to `main` and on tags.

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
  CLI in a sidecar. **✓ Shipped — see `/slicer`.**
