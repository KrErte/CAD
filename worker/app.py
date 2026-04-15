"""
CadQuery worker — receives structured parameters, returns STL.

POST /generate
  { "template": "shelf_bracket",
    "params": { "pipe_diameter": 32, "load_kg": 5, "arm_length": 120 } }
  -> application/sla (STL binary)

GET /templates
  -> list available templates + their param schema
"""
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel
from typing import Dict, Any
import cadquery as cq
import io
import tempfile
import os

app = FastAPI(title="AI-CAD Worker", version="0.1.0")


class GenRequest(BaseModel):
    template: str
    params: Dict[str, Any]


# --- Templates ---------------------------------------------------------------

TEMPLATES = {}


def register(name, schema):
    def deco(fn):
        TEMPLATES[name] = {"fn": fn, "schema": schema}
        return fn
    return deco


@register(
    "shelf_bracket",
    {
        "description": "L-kujuline riiuliklamber torule kinnitamiseks",
        "params": {
            "pipe_diameter": {"type": "number", "unit": "mm", "min": 10, "max": 60, "default": 32},
            "load_kg": {"type": "number", "unit": "kg", "min": 1, "max": 30, "default": 5},
            "arm_length": {"type": "number", "unit": "mm", "min": 40, "max": 200, "default": 120},
            "wall_thickness": {"type": "number", "unit": "mm", "min": 3, "max": 10, "default": 5},
        },
    },
)
def shelf_bracket(pipe_diameter=32, load_kg=5, arm_length=120, wall_thickness=5):
    """
    L-bracket: vertical clamp around a pipe + horizontal arm supporting the shelf.
    Wall thickness scales automatically with load for safety.
    """
    # auto-scale thickness up for heavier loads
    t = max(wall_thickness, 3 + 0.4 * load_kg)
    r_out = pipe_diameter / 2 + t
    r_in = pipe_diameter / 2

    # Vertical cylindrical clamp (split ring with bolt hole)
    clamp = (
        cq.Workplane("XY")
        .circle(r_out)
        .circle(r_in)
        .extrude(pipe_diameter + 2 * t)
    )
    # Cut an opening for the bolt clamp
    clamp = clamp.faces(">Z").workplane().center(r_out, 0).rect(t * 2, 8).cutThruAll()

    # Horizontal arm
    arm = (
        cq.Workplane("XY")
        .box(arm_length, t * 4, t, centered=(False, True, False))
        .translate((r_out, 0, pipe_diameter / 2 + t / 2))
    )
    # Screw hole near arm tip
    arm = (
        arm.faces(">Z")
        .workplane(origin=(r_out + arm_length - 15, 0, 0))
        .hole(5)
    )

    model = clamp.union(arm)
    return model


@register(
    "hook",
    {
        "description": "Seinakonks koormusele X kg",
        "params": {
            "load_kg": {"type": "number", "unit": "kg", "min": 1, "max": 20, "default": 3},
            "reach": {"type": "number", "unit": "mm", "min": 20, "max": 120, "default": 50},
        },
    },
)
def hook(load_kg=3, reach=50):
    t = max(4, 2 + 0.5 * load_kg)
    base = cq.Workplane("XY").box(40, 20, t)
    base = base.faces(">Z").workplane(origin=(-15, 0, 0)).hole(5)
    base = base.faces(">Z").workplane(origin=(15, 0, 0)).hole(5)
    arm = (
        cq.Workplane("YZ")
        .moveTo(0, t / 2)
        .lineTo(reach, t / 2)
        .lineTo(reach, t / 2 + 25)
        .lineTo(reach - 15, t / 2 + 25)
        .lineTo(reach - 15, t / 2 + 5)
        .lineTo(0, t / 2 + 5)
        .close()
        .extrude(t)
        .translate((-t / 2, 0, 0))
    )
    return base.union(arm)


# --- Routes ------------------------------------------------------------------


@app.get("/templates")
def list_templates():
    return {
        name: {"description": tpl["schema"]["description"], "params": tpl["schema"]["params"]}
        for name, tpl in TEMPLATES.items()
    }


@app.get("/health")
def health():
    return {"status": "ok", "templates": list(TEMPLATES.keys())}


@app.post("/generate")
def generate(req: GenRequest):
    tpl = TEMPLATES.get(req.template)
    if not tpl:
        raise HTTPException(404, f"Unknown template: {req.template}")
    try:
        model = tpl["fn"](**req.params)
    except TypeError as e:
        raise HTTPException(400, f"Bad params: {e}")

    with tempfile.NamedTemporaryFile(suffix=".stl", delete=False) as f:
        path = f.name
    try:
        cq.exporters.export(model, path)
        with open(path, "rb") as fh:
            data = fh.read()
    finally:
        os.unlink(path)

    return Response(
        content=data,
        media_type="application/sla",
        headers={"Content-Disposition": f'attachment; filename="{req.template}.stl"'},
    )
