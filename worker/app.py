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
    "box",
    {
        "description": "Lihtne kandiline karp koos kaanega (kaas eraldi printida)",
        "params": {
            "length": {"type": "number", "unit": "mm", "min": 20, "max": 300, "default": 80},
            "width":  {"type": "number", "unit": "mm", "min": 20, "max": 300, "default": 60},
            "height": {"type": "number", "unit": "mm", "min": 10, "max": 200, "default": 40},
            "wall":   {"type": "number", "unit": "mm", "min": 1.5, "max": 8, "default": 2.5},
        },
    },
)
def box(length=80, width=60, height=40, wall=2.5):
    outer = cq.Workplane("XY").box(length, width, height, centered=(True, True, False))
    inner = (
        cq.Workplane("XY")
        .box(length - 2 * wall, width - 2 * wall, height - wall, centered=(True, True, False))
        .translate((0, 0, wall))
    )
    return outer.cut(inner)


@register(
    "adapter",
    {
        "description": "Toruline adapter ühe diameetri vahetamiseks teiseks",
        "params": {
            "d_in":   {"type": "number", "unit": "mm", "min": 5,  "max": 100, "default": 25},
            "d_out":  {"type": "number", "unit": "mm", "min": 5,  "max": 100, "default": 32},
            "length": {"type": "number", "unit": "mm", "min": 10, "max": 200, "default": 40},
            "wall":   {"type": "number", "unit": "mm", "min": 1.5, "max": 6, "default": 2.5},
        },
    },
)
def adapter(d_in=25, d_out=32, length=40, wall=2.5):
    half = length / 2
    bottom = (
        cq.Workplane("XY")
        .circle(d_in / 2 + wall).circle(d_in / 2)
        .extrude(half)
    )
    top = (
        cq.Workplane("XY")
        .circle(d_out / 2 + wall).circle(d_out / 2)
        .extrude(half)
        .translate((0, 0, half))
    )
    # Conical transition (loft would be ideal; for robustness we just stack)
    return bottom.union(top)


@register(
    "cable_clamp",
    {
        "description": "Kaabli- või juhtmehoidik seinale (n kaablit kõrvuti)",
        "params": {
            "cable_diameter": {"type": "number", "unit": "mm", "min": 2,  "max": 30, "default": 6},
            "count":          {"type": "number", "unit": "tk", "min": 1,  "max": 10, "default": 3},
            "screw_hole":     {"type": "number", "unit": "mm", "min": 3,  "max": 8,  "default": 4},
        },
    },
)
def cable_clamp(cable_diameter=6, count=3, screw_hole=4):
    # Horizontal P-clamp: base plate with 2 end screw holes + body with
    # tunnels going right through in the Y direction for cables.
    spacing = cable_diameter + 4
    width = spacing * count + 20
    depth = 18
    base_h = 4
    body_h = cable_diameter + 6

    base = cq.Workplane("XY").box(width, depth, base_h, centered=(True, True, False))
    base = (base.faces(">Z").workplane()
            .pushPoints([(-width / 2 + 5, 0), (width / 2 - 5, 0)])
            .hole(screw_hole))

    body = (cq.Workplane("XY")
            .box(width, depth, body_h, centered=(True, True, False))
            .translate((0, 0, base_h)))

    for i in range(int(count)):
        x = -((count - 1) / 2) * spacing + i * spacing
        z = base_h + body_h - cable_diameter / 2 - 0.5
        tunnel = (cq.Workplane("XZ")
                  .moveTo(x, z)
                  .circle(cable_diameter / 2)
                  .extrude(depth + 4)
                  .translate((0, -(depth + 4) / 2, 0)))
        body = body.cut(tunnel)
    return base.union(body)


@register(
    "tag",
    {
        "description": "Lapik silt augukesega (võtmehoidja, lemmiklooma plaat)",
        "params": {
            "length":    {"type": "number", "unit": "mm", "min": 20, "max": 120, "default": 50},
            "width":     {"type": "number", "unit": "mm", "min": 10, "max": 60,  "default": 25},
            "thickness": {"type": "number", "unit": "mm", "min": 1.5, "max": 6,  "default": 3},
            "hole":      {"type": "number", "unit": "mm", "min": 2,  "max": 8,   "default": 4},
        },
    },
)
def tag(length=50, width=25, thickness=3, hole=4):
    body = (
        cq.Workplane("XY")
        .box(length, width, thickness, centered=(True, True, False))
        .edges("|Z").fillet(min(width, length) / 4)
    )
    return body.faces(">Z").workplane(origin=(-length / 2 + width / 2, 0, 0)).hole(hole)


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
