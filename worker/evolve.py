"""
Darwin CAD — evolutsiooniline parameetriline disain.

Idee: standardne text-to-CAD annab ÜHE vastuse. Darwin CAD annab POPULATSIOONI
6–8 varianti. Claude Vision hindab neid fitness-funktsioonina. Kasutaja valib
parima, järgmine generatsioon evolveerub sellest. 2–3 generatsiooni hiljem on
lõppvariant kvaliteetsem kui ühtegi üksik-genereering, mis sa oleksid tavalise
pipeline'iga saanud.

See on tõeliselt enneolematu — ei Zoo, Adam ega Backflip ei tee seda.

Tehniline voog:
  POST /evolve/seed     { template, prompt_et, n=6 }
      → tagastab N varianti (iga random-sampling'iga templaadi min/max-i piires)
      → iga variandi jaoks: {variant_id, params, svg_thumb, metrics}

  POST /evolve/cross    { parents: [{spec,...}, {spec,...}], n=6, mutation=0.2 }
      → ristamine kahe vanemvariandi vahel (uniform crossover)
      → mutatsioon (±20% iga parameetri juures, klipitud min/max-i)
      → tagastab N uue generatsiooni varianti

SVG-eelvaade (mitte STL) on genereerimiseks väga odav — 50–100ms vs 2s STL-i
jaoks. Nii saame 6 varianti alla sekundi jooksul. Backend edastab SVG-d frontendi
galeriile; STL genereeritakse alles siis, kui kasutaja valib välja.

AST sandbox ei ole siin vajalik, sest me opereerime TEMPLATE-ite peal (mitte
Claude-genereeritud koodil). Freeform script-gen on eraldi failis (freeform.py).
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Dict, Any, List, Optional
import cadquery as cq
import io
import math
import random
import secrets
import time
import hashlib
import json

router = APIRouter(prefix="/evolve", tags=["evolve"])


# --- Mudelid --------------------------------------------------------------

class SeedRequest(BaseModel):
    """Esimene põlvkond — puhas juhuslik sampling template'i min/max-ist."""
    template: str
    prompt_et: Optional[str] = None          # vaba kontekst, ainult logi jaoks
    n: int = Field(default=6, ge=2, le=12)
    seed: Optional[int] = None                # reproducible evolution


class Variant(BaseModel):
    """Üks indiviid populatsioonis."""
    variant_id: str
    generation: int
    template: str
    params: Dict[str, Any]
    svg_thumb: str                             # inline SVG kui string
    metrics: Dict[str, Any]                    # volume_cm3, weight_g, bbox, overhang
    ancestry: List[str] = Field(default_factory=list)   # eellaste variant_id-d


class CrossRequest(BaseModel):
    """Järgmise põlvkonna genereerimine."""
    parents: List[Variant]                     # 1 või 2 vanemat
    n: int = Field(default=6, ge=2, le=12)
    mutation: float = Field(default=0.2, ge=0.0, le=0.5)  # ±% per param
    seed: Optional[int] = None


class Population(BaseModel):
    """Evolve API vastus — üks põlvkond."""
    generation: int
    variants: List[Variant]
    elapsed_ms: int


# --- Abivahendid -----------------------------------------------------------

def _clamp(x: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, x))


def _sample_params(schema: Dict[str, Any], rng: random.Random,
                   around: Optional[Dict[str, Any]] = None,
                   mutation: float = 1.0) -> Dict[str, Any]:
    """
    Kui around=None → puhas juhuslik sampling min/max-i piires.
    Kui around=olemasolev param dict → gaussian muutus selle ümber,
        kus sigma = mutation * (max - min) / 6 (3-sigma ≈ kogu ulatus).
    """
    out: Dict[str, Any] = {}
    for name, spec in schema["params"].items():
        lo = spec["min"]
        hi = spec["max"]
        unit = spec.get("unit", "")
        if around is None:
            v = rng.uniform(lo, hi)
        else:
            base = around.get(name, spec.get("default", (lo + hi) / 2))
            sigma = mutation * (hi - lo) / 6.0
            v = rng.gauss(base, sigma)
            v = _clamp(v, lo, hi)
        # Täiarvu-sarnased ühikud (tk, deg mõnikord) — ümarda
        if unit in ("tk",):
            v = int(round(v))
            v = int(_clamp(v, lo, hi))
        else:
            # ühe kümnendkoha täpsus on täiesti piisav 3D-prindi jaoks
            v = round(v, 1)
        out[name] = v
    return out


def _crossover(p1: Dict[str, Any], p2: Dict[str, Any],
               rng: random.Random) -> Dict[str, Any]:
    """Uniform crossover — iga geeni (parameetri) jaoks 50/50 kas p1 või p2."""
    child: Dict[str, Any] = {}
    for k in p1.keys():
        child[k] = p1[k] if rng.random() < 0.5 else p2.get(k, p1[k])
    return child


def _variant_id(template: str, params: Dict[str, Any]) -> str:
    """Deterministic ID — kui sama template+params, sama ID (caching)."""
    s = template + json.dumps(params, sort_keys=True, separators=(",", ":"))
    return hashlib.sha1(s.encode()).hexdigest()[:12]


def _render_svg(model, size: int = 240) -> str:
    """
    SVG-eelvaade isomeetrilisest vaatest. Kasutame CadQuery sisseehitatud
    SVG-eksporti — kiire (100ms), pildi kvaliteet piisab thumbnail-i jaoks.
    Tagastame raw SVG stringi (frontend saab selle otse DOM-i panna).
    """
    try:
        # cq.exporters.getSVG aktsepteerib Shape-i, mitte Workplane-i — võta .val()
        shape = model.val() if hasattr(model, "val") else model
        svg = cq.exporters.getSVG(
            shape,
            opts={
                "width": size, "height": size,
                "marginLeft": 10, "marginTop": 10,
                "showAxes": False,
                "projectionDir": (1.0, -1.0, 0.8),   # isomeetriline
                "strokeWidth": 0.8,
                "strokeColor": (30, 30, 30),
                "hiddenColor": (200, 200, 200),
                "showHidden": True,
            },
        )
        return svg
    except Exception as e:
        # Fallback: lihtne tühi SVG textiga — loop ei tohi katkeda
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}">'
                f'<rect width="100%" height="100%" fill="#f4f4f4"/>'
                f'<text x="50%" y="50%" text-anchor="middle" dy=".3em" '
                f'fill="#c8102e" font-size="12">render failed</text></svg>')


def _cheap_metrics(model) -> Dict[str, Any]:
    """Samad odavad heuristikud kui /metrics endpoint-is."""
    solid = model.val() if hasattr(model, "val") else model
    vol_mm3 = float(solid.Volume())
    bb = solid.BoundingBox()
    size = (bb.xlen, bb.ylen, bb.zlen)
    weight_g = (vol_mm3 / 1000.0) * 1.24
    print_min = max(5, round((vol_mm3 / 1000.0) * 3.5))
    overhang_risk = size[2] > 2 * max(size[0], size[1]) and min(size[0], size[1]) < 15
    return {
        "volume_cm3": round(vol_mm3 / 1000.0, 2),
        "bbox_mm": {"x": round(size[0], 1), "y": round(size[1], 1), "z": round(size[2], 1)},
        "weight_g_pla": round(weight_g, 1),
        "print_time_min_estimate": print_min,
        "overhang_risk": overhang_risk,
    }


# --- Põhifunktsioonid ------------------------------------------------------

def _build_variant(TEMPLATES: Dict[str, Any], template: str,
                   params: Dict[str, Any], generation: int,
                   ancestry: List[str]) -> Variant:
    tpl = TEMPLATES.get(template)
    if not tpl:
        raise HTTPException(404, f"Unknown template: {template}")
    try:
        model = tpl["fn"](**params)
    except Exception as e:
        # Kui mutatsioon andis halba kombinatsiooni (nt wall > inner/2) — skipime
        raise HTTPException(400, f"Variant build failed: {e}")
    svg = _render_svg(model)
    metrics = _cheap_metrics(model)
    vid = _variant_id(template, params)
    return Variant(
        variant_id=vid,
        generation=generation,
        template=template,
        params=params,
        svg_thumb=svg,
        metrics=metrics,
        ancestry=ancestry,
    )


def register_routes(app, TEMPLATES: Dict[str, Any]):
    """Worker app.py registreerib selle moodul mount'i ajal."""

    @app.post("/evolve/seed", response_model=Population)
    def seed(req: SeedRequest):
        """
        Esimene põlvkond — puhas juhuslik sampling templaadi min/max-ist.
        Tagastab N varianti, igaüks SVG-eelvaatega ja heuristiliste mõõtudega.
        """
        t0 = time.perf_counter()
        tpl = TEMPLATES.get(req.template)
        if not tpl:
            raise HTTPException(404, f"Unknown template: {req.template}")
        rng = random.Random(req.seed if req.seed is not None
                            else secrets.randbits(32))
        variants: List[Variant] = []
        attempts = 0
        # Retry iga variandi kohta kuni max 3x — mõni random kombinatsioon
        # võib CadQuery viskada "thin wall" vms
        while len(variants) < req.n and attempts < req.n * 3:
            attempts += 1
            params = _sample_params(tpl["schema"], rng)
            try:
                v = _build_variant(TEMPLATES, req.template, params,
                                   generation=1, ancestry=[])
                variants.append(v)
            except HTTPException:
                continue
        if not variants:
            raise HTTPException(500, "Failed to generate any valid variants")
        return Population(
            generation=1,
            variants=variants,
            elapsed_ms=int((time.perf_counter() - t0) * 1000),
        )

    @app.post("/evolve/cross", response_model=Population)
    def cross(req: CrossRequest):
        """
        Järgmine põlvkond — ristamine vanemate vahel + mutatsioon.
        Kui üks vanem → puhas mutatsioon. Kui kaks → uniform crossover + mut.
        Kui kolm+ → võetakse juhuslikult 2 iga lapse jaoks.
        """
        t0 = time.perf_counter()
        if not req.parents:
            raise HTTPException(400, "At least one parent required")
        # Kõik vanemad peavad olema sama template'iga — sinisele-lillale
        # ristandaja eelduseks
        tmpl = req.parents[0].template
        if any(p.template != tmpl for p in req.parents):
            raise HTTPException(400, "All parents must share the same template")
        tpl = TEMPLATES.get(tmpl)
        if not tpl:
            raise HTTPException(404, f"Unknown template: {tmpl}")

        rng = random.Random(req.seed if req.seed is not None
                            else secrets.randbits(32))
        gen = max(p.generation for p in req.parents) + 1
        variants: List[Variant] = []
        attempts = 0
        while len(variants) < req.n and attempts < req.n * 3:
            attempts += 1
            if len(req.parents) == 1:
                base = req.parents[0].params
                ancestry = [req.parents[0].variant_id]
            else:
                pair = rng.sample(req.parents, 2)
                base = _crossover(pair[0].params, pair[1].params, rng)
                ancestry = [pair[0].variant_id, pair[1].variant_id]
            # Mutatsioon — Gaussian around crossover tulemus
            params = _sample_params(tpl["schema"], rng,
                                    around=base, mutation=req.mutation)
            try:
                v = _build_variant(TEMPLATES, tmpl, params,
                                   generation=gen, ancestry=ancestry)
                variants.append(v)
            except HTTPException:
                continue
        if not variants:
            raise HTTPException(500, "Failed to generate any valid offspring")
        return Population(
            generation=gen,
            variants=variants,
            elapsed_ms=int((time.perf_counter() - t0) * 1000),
        )

    @app.get("/evolve/health")
    def health():
        return {"ok": True, "templates": len(TEMPLATES)}
