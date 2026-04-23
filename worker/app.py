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

# Darwin CAD + freeform script-gen moodulid — need saavad ligipääsu TEMPLATES
# dict-ile allpool määratluse kaudu (register_routes kutsutakse pärast failinna
# lõppu).
from evolve import register_routes as _register_evolve
from freeform import register_routes as _register_freeform
from printflow import register_routes as _register_printflow
from dfm import register_routes as _register_dfm

# Observability — /metrics endpoint + OTel tracing (no-op kui env pole seatud)
from observability import setup_observability

app = FastAPI(title="AI-CAD Worker", version="0.2.0")
setup_observability(app, service_name="ai-cad-worker")


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


@register(
    "vesa_adapter",
    {
        "description": "VESA monitor-adapter (kvadraat kruviauke)",
        "params": {
            "size": {"type": "number", "unit": "mm", "min": 50, "max": 200, "default": 100},
            "hole_spacing": {"type": "number", "unit": "mm", "min": 50, "max": 200, "default": 100},
            "hole_diameter": {"type": "number", "unit": "mm", "min": 3, "max": 8, "default": 5},
            "thickness": {"type": "number", "unit": "mm", "min": 3, "max": 10, "default": 5},
        },
    },
)
def vesa_adapter(size=100, hole_spacing=100, hole_diameter=5, thickness=5):
    plate = cq.Workplane("XY").box(size, size, thickness, centered=(True, True, False))
    half = hole_spacing / 2
    plate = (plate.faces(">Z").workplane()
             .pushPoints([(half, half), (-half, half), (half, -half), (-half, -half)])
             .hole(hole_diameter))
    # 4 lightening holes in center
    plate = plate.faces(">Z").workplane().hole(min(20, size / 4))
    return plate


@register(
    "enclosure",
    {
        "description": "Elektroonika-karp koos kaane-kinnituskõrvadega",
        "params": {
            "width": {"type": "number", "unit": "mm", "min": 40, "max": 200, "default": 80},
            "depth": {"type": "number", "unit": "mm", "min": 40, "max": 200, "default": 60},
            "height": {"type": "number", "unit": "mm", "min": 20, "max": 100, "default": 35},
            "wall": {"type": "number", "unit": "mm", "min": 1.5, "max": 5, "default": 2},
            "screw_hole": {"type": "number", "unit": "mm", "min": 2, "max": 5, "default": 3},
        },
    },
)
def enclosure(width=80, depth=60, height=35, wall=2, screw_hole=3):
    outer = cq.Workplane("XY").box(width, depth, height, centered=(True, True, False))
    inner = (cq.Workplane("XY")
             .box(width - 2 * wall, depth - 2 * wall, height - wall,
                  centered=(True, True, False))
             .translate((0, 0, wall)))
    body = outer.cut(inner)
    # corner bosses for lid screws
    boss_r = screw_hole / 2 + 2
    for (dx, dy) in [(1, 1), (1, -1), (-1, 1), (-1, -1)]:
        cx = dx * (width / 2 - wall - boss_r - 0.5)
        cy = dy * (depth / 2 - wall - boss_r - 0.5)
        boss = (cq.Workplane("XY").circle(boss_r).extrude(height - wall)
                .translate((cx, cy, wall)))
        boss = boss.faces(">Z").workplane().hole(screw_hole, depth=8)
        body = body.union(boss)
    return body


@register(
    "wall_mount",
    {
        "description": "Seinakinnitus (plaat 2 kruviauguga + väljaulatuv nukk)",
        "params": {
            "width": {"type": "number", "unit": "mm", "min": 40, "max": 150, "default": 60},
            "height": {"type": "number", "unit": "mm", "min": 30, "max": 150, "default": 50},
            "thickness": {"type": "number", "unit": "mm", "min": 3, "max": 10, "default": 4},
            "screw_hole": {"type": "number", "unit": "mm", "min": 3, "max": 8, "default": 5},
            "peg_diameter": {"type": "number", "unit": "mm", "min": 5, "max": 25, "default": 10},
            "peg_length": {"type": "number", "unit": "mm", "min": 10, "max": 60, "default": 25},
        },
    },
)
def wall_mount(width=60, height=50, thickness=4, screw_hole=5, peg_diameter=10, peg_length=25):
    plate = cq.Workplane("XY").box(width, height, thickness, centered=(True, True, False))
    y_off = height / 2 - 10
    plate = (plate.faces(">Z").workplane()
             .pushPoints([(0, y_off), (0, -y_off)])
             .hole(screw_hole))
    peg = (cq.Workplane("XY").circle(peg_diameter / 2).extrude(peg_length)
           .translate((0, 0, thickness)))
    return plate.union(peg)


@register(
    "u_channel_clip",
    {
        "description": "U-kujuline klamber kaablikanalile või profiilile",
        "params": {
            "inner_width": {"type": "number", "unit": "mm", "min": 5, "max": 40, "default": 16},
            "inner_depth": {"type": "number", "unit": "mm", "min": 5, "max": 40, "default": 12},
            "wall": {"type": "number", "unit": "mm", "min": 2, "max": 6, "default": 3},
            "length": {"type": "number", "unit": "mm", "min": 10, "max": 80, "default": 30},
        },
    },
)
def u_channel_clip(inner_width=16, inner_depth=12, wall=3, length=30):
    outer_w = inner_width + 2 * wall
    outer_d = inner_depth + wall
    outer = cq.Workplane("XY").box(outer_w, outer_d, length, centered=(True, True, False))
    inner = (cq.Workplane("XY")
             .box(inner_width, inner_depth + wall, length + 2,
                  centered=(True, True, False))
             .translate((0, wall, -1)))
    return outer.cut(inner)


@register(
    "spool_holder_clip",
    {
        "description": "Lihtne filamendi-spoolihoidja klipp printerile",
        "params": {
            "shaft_diameter": {"type": "number", "unit": "mm", "min": 5, "max": 12, "default": 8},
            "spool_hole": {"type": "number", "unit": "mm", "min": 20, "max": 80, "default": 52},
            "width": {"type": "number", "unit": "mm", "min": 20, "max": 80, "default": 40},
            "thickness": {"type": "number", "unit": "mm", "min": 3, "max": 10, "default": 5},
        },
    },
)
def spool_holder_clip(shaft_diameter=8, spool_hole=52, width=40, thickness=5):
    body = (cq.Workplane("XY").circle(spool_hole / 2 - 1).extrude(thickness)
            .faces(">Z").workplane().circle(shaft_diameter / 2 + 1).cutThruAll())
    wing = (cq.Workplane("XY").box(width, spool_hole / 2, thickness,
                                   centered=(True, False, False)))
    return body.union(wing)


@register(
    "tool_holder",
    {
        "description": "Tööriista-hoidja seinale (üks ava)",
        "params": {
            "tool_diameter": {"type": "number", "unit": "mm", "min": 5, "max": 50, "default": 20},
            "depth": {"type": "number", "unit": "mm", "min": 15, "max": 80, "default": 30},
            "wall": {"type": "number", "unit": "mm", "min": 2, "max": 6, "default": 3},
            "screw_hole": {"type": "number", "unit": "mm", "min": 3, "max": 6, "default": 4},
        },
    },
)
def tool_holder(tool_diameter=20, depth=30, wall=3, screw_hole=4):
    outer_r = tool_diameter / 2 + wall
    mount_w = outer_r * 2 + 20
    plate = cq.Workplane("XY").box(mount_w, depth, wall, centered=(True, True, False))
    plate = (plate.faces(">Z").workplane()
             .pushPoints([(-mount_w / 2 + 5, 0), (mount_w / 2 - 5, 0)])
             .hole(screw_hole))
    tube = (cq.Workplane("XY").circle(outer_r)
            .extrude(depth)
            .faces(">Z").workplane().circle(tool_diameter / 2).cutThruAll()
            .translate((0, 0, wall)))
    return plate.union(tube)


@register(
    "threaded_insert_boss",
    {
        "description": "M-keerme-sisestuspuksi boss (kuumpressitav messinginsert)",
        "params": {
            "insert_diameter": {"type": "number", "unit": "mm", "min": 3, "max": 8, "default": 4.2},
            "insert_depth": {"type": "number", "unit": "mm", "min": 4, "max": 15, "default": 6},
            "outer_diameter": {"type": "number", "unit": "mm", "min": 6, "max": 15, "default": 8},
            "height": {"type": "number", "unit": "mm", "min": 6, "max": 25, "default": 10},
        },
    },
)
def threaded_insert_boss(insert_diameter=4.2, insert_depth=6, outer_diameter=8, height=10):
    boss = (cq.Workplane("XY").circle(outer_diameter / 2).extrude(height)
            .faces(">Z").workplane()
            .circle(insert_diameter / 2).cutBlind(-insert_depth))
    return boss


@register(
    "cable_grommet",
    {
        "description": "Kaabliläbiviigu-tihend (paneeli auku)",
        "params": {
            "cable_diameter": {"type": "number", "unit": "mm", "min": 3, "max": 20, "default": 6},
            "panel_hole": {"type": "number", "unit": "mm", "min": 8, "max": 40, "default": 16},
            "panel_thickness": {"type": "number", "unit": "mm", "min": 1, "max": 8, "default": 2},
        },
    },
)
def cable_grommet(cable_diameter=6, panel_hole=16, panel_thickness=2):
    flange_r = panel_hole / 2 + 4
    flange_h = 2
    neck_r = panel_hole / 2
    neck_h = panel_thickness
    flange = cq.Workplane("XY").circle(flange_r).extrude(flange_h)
    neck = (cq.Workplane("XY").circle(neck_r).extrude(neck_h)
            .translate((0, 0, flange_h)))
    top_flange = (cq.Workplane("XY").circle(flange_r).extrude(flange_h)
                  .translate((0, 0, flange_h + neck_h)))
    body = flange.union(neck).union(top_flange)
    return body.faces(">Z").workplane().hole(cable_diameter)


@register(
    "label_plate",
    {
        "description": "Märgistustahvel kruvikinnitusega (lame, kandeline)",
        "params": {
            "width": {"type": "number", "unit": "mm", "min": 30, "max": 150, "default": 60},
            "height": {"type": "number", "unit": "mm", "min": 15, "max": 80, "default": 25},
            "thickness": {"type": "number", "unit": "mm", "min": 1.5, "max": 5, "default": 2},
            "screw_hole": {"type": "number", "unit": "mm", "min": 2, "max": 5, "default": 3},
        },
    },
)
def label_plate(width=60, height=25, thickness=2, screw_hole=3):
    plate = (cq.Workplane("XY").box(width, height, thickness, centered=(True, True, False))
             .edges("|Z").fillet(min(height / 3, 5)))
    hx = width / 2 - 5
    plate = (plate.faces(">Z").workplane()
             .pushPoints([(hx, 0), (-hx, 0)])
             .hole(screw_hole))
    return plate


@register(
    "printer_bed_clip",
    {
        "description": "3D-printeri aluseplaadi klamber (fikseerib klaasplaati)",
        "params": {
            "bed_thickness": {"type": "number", "unit": "mm", "min": 2, "max": 10, "default": 4},
            "grip": {"type": "number", "unit": "mm", "min": 5, "max": 20, "default": 10},
            "screw_hole": {"type": "number", "unit": "mm", "min": 3, "max": 6, "default": 3.5},
        },
    },
)
def printer_bed_clip(bed_thickness=4, grip=10, screw_hole=3.5):
    width = 20
    length = grip + 25  # mounting tail + grip
    thickness = 3
    # L-shape: horizontal plate + upward lip at end
    base = cq.Workplane("XY").box(length, width, thickness, centered=(True, True, False))
    base = (base.faces(">Z").workplane()
            .moveTo(-length / 2 + 10, 0)
            .hole(screw_hole))
    lip = (cq.Workplane("XY")
           .box(grip, width, bed_thickness + thickness, centered=(True, True, False))
           .translate((length / 2 - grip / 2, 0, thickness)))
    # Cut slot for bed
    slot = (cq.Workplane("XY")
            .box(grip + 2, width + 2, bed_thickness, centered=(True, True, False))
            .translate((length / 2 - grip / 2, 0, thickness)))
    return base.union(lip).cut(slot)


@register(
    "living_hinge",
    {
        "description": "Painduv hing (living hinge) — üks trükk, voltib kokku",
        "params": {
            "width": {"type": "number", "unit": "mm", "min": 20, "max": 150, "default": 60},
            "panel_length": {"type": "number", "unit": "mm", "min": 15, "max": 100, "default": 40},
            "thickness": {"type": "number", "unit": "mm", "min": 1.5, "max": 5, "default": 3},
            "hinge_cuts": {"type": "number", "unit": "tk", "min": 3, "max": 15, "default": 7},
        },
    },
)
def living_hinge(width=60, panel_length=40, thickness=3, hinge_cuts=7):
    hinge_width = 8
    cut_gap = 0.8
    # Two flat panels
    left = cq.Workplane("XY").box(panel_length, width, thickness, centered=(False, True, False))
    right = (cq.Workplane("XY").box(panel_length, width, thickness, centered=(False, True, False))
             .translate((panel_length + hinge_width, 0, 0)))
    # Hinge bridge
    bridge = (cq.Workplane("XY")
              .box(hinge_width, width, thickness, centered=(False, True, False))
              .translate((panel_length, 0, 0)))
    # Alternating cuts for flexibility
    n = int(hinge_cuts)
    step = width / (n + 1)
    for i in range(n):
        y = -width / 2 + step * (i + 1)
        offset = 0 if i % 2 == 0 else hinge_width / 2
        slot = (cq.Workplane("XY")
                .box(hinge_width * 0.7, cut_gap, thickness + 2, centered=(True, True, False))
                .translate((panel_length + hinge_width / 2 + offset * 0.3, y, -1)))
        bridge = bridge.cut(slot)
    return left.union(bridge).union(right)


@register(
    "snap_fit_clip",
    {
        "description": "Snap-fit kinnitus — ühe vajutusega lukustub",
        "params": {
            "width": {"type": "number", "unit": "mm", "min": 5, "max": 30, "default": 10},
            "arm_length": {"type": "number", "unit": "mm", "min": 10, "max": 50, "default": 25},
            "thickness": {"type": "number", "unit": "mm", "min": 1, "max": 4, "default": 1.5},
            "hook_height": {"type": "number", "unit": "mm", "min": 1, "max": 5, "default": 2},
            "base_height": {"type": "number", "unit": "mm", "min": 3, "max": 15, "default": 6},
        },
    },
)
def snap_fit_clip(width=10, arm_length=25, thickness=1.5, hook_height=2, base_height=6):
    # Base block
    base = cq.Workplane("XY").box(width, 10, base_height, centered=(True, True, False))
    # Cantilever arm going up
    arm = (cq.Workplane("XY")
           .box(width, thickness, arm_length, centered=(True, True, False))
           .translate((0, 0, base_height)))
    # Hook at the tip
    hook = (cq.Workplane("XY")
            .box(width, thickness + hook_height, thickness, centered=(True, True, False))
            .translate((0, hook_height / 2, base_height + arm_length)))
    return base.union(arm).union(hook)


@register(
    "spur_gear",
    {
        "description": "Sirghambaga hammasratas (spur gear)",
        "params": {
            "module": {"type": "number", "unit": "mm", "min": 0.5, "max": 5, "default": 2},
            "teeth": {"type": "number", "unit": "tk", "min": 8, "max": 60, "default": 20},
            "thickness": {"type": "number", "unit": "mm", "min": 3, "max": 30, "default": 8},
            "bore": {"type": "number", "unit": "mm", "min": 2, "max": 15, "default": 5},
        },
    },
)
def spur_gear(module=2, teeth=20, thickness=8, bore=5):
    import math
    n = int(teeth)
    m = module
    r_pitch = n * m / 2
    r_outer = r_pitch + m
    r_root = r_pitch - 1.25 * m
    r_base = r_pitch * math.cos(math.radians(20))

    # Approximate gear profile with polygon
    pts = []
    for i in range(n):
        angle = 2 * math.pi * i / n
        # Tooth tip
        a1 = angle - math.pi / (2 * n)
        a2 = angle + math.pi / (2 * n)
        # Root
        a0 = angle - math.pi / n
        a3 = angle + math.pi / n

        pts.append((r_root * math.cos(a0), r_root * math.sin(a0)))
        pts.append((r_outer * math.cos(a1), r_outer * math.sin(a1)))
        pts.append((r_outer * math.cos(a2), r_outer * math.sin(a2)))
        pts.append((r_root * math.cos(a3), r_root * math.sin(a3)))

    gear = (cq.Workplane("XY")
            .polyline(pts).close()
            .extrude(thickness))
    # Center bore
    gear = gear.faces(">Z").workplane().hole(bore)
    return gear


@register(
    "din_rail_clip",
    {
        "description": "DIN-rööpa (35mm) kinnitus klamber",
        "params": {
            "width": {"type": "number", "unit": "mm", "min": 15, "max": 80, "default": 30},
            "depth": {"type": "number", "unit": "mm", "min": 15, "max": 50, "default": 25},
            "wall": {"type": "number", "unit": "mm", "min": 2, "max": 5, "default": 3},
        },
    },
)
def din_rail_clip(width=30, depth=25, wall=3):
    # DIN rail is 35mm wide, 7.5mm deep
    rail_w = 35
    rail_d = 7.5
    lip = 2
    base_h = rail_d + wall
    # Body wrapping around DIN rail
    outer = cq.Workplane("XY").box(rail_w + 2 * wall, depth, base_h, centered=(True, True, False))
    # Cut the rail channel
    channel = (cq.Workplane("XY")
               .box(rail_w, depth + 2, rail_d, centered=(True, True, False))
               .translate((0, 0, wall)))
    body = outer.cut(channel)
    # Retention lips at bottom
    for dx in [1, -1]:
        lip_block = (cq.Workplane("XY")
                     .box(lip, depth, lip, centered=(True, True, False))
                     .translate((dx * (rail_w / 2 + wall / 2), 0, wall)))
        body = body.union(lip_block)
    # Mounting platform on top
    platform = (cq.Workplane("XY")
                .box(width, depth, wall, centered=(True, True, False))
                .translate((0, 0, base_h)))
    body = body.union(platform)
    # Screw holes
    if width > 20:
        body = (body.faces(">Z").workplane()
                .pushPoints([(-width / 4, 0), (width / 4, 0)])
                .hole(3.5))
    return body


@register(
    "phone_stand",
    {
        "description": "Lauapealne telefonihoidja (seisev, nurga all)",
        "params": {
            "width": {"type": "number", "unit": "mm", "min": 40, "max": 120, "default": 75},
            "depth": {"type": "number", "unit": "mm", "min": 40, "max": 100, "default": 60},
            "angle": {"type": "number", "unit": "deg", "min": 30, "max": 80, "default": 65},
            "lip_height": {"type": "number", "unit": "mm", "min": 5, "max": 20, "default": 10},
            "thickness": {"type": "number", "unit": "mm", "min": 2, "max": 6, "default": 3},
        },
    },
)
def phone_stand(width=75, depth=60, angle=65, lip_height=10, thickness=3):
    import math
    # Base plate
    base = cq.Workplane("XY").box(width, depth, thickness, centered=(True, True, False))
    # Front lip to hold phone
    lip = (cq.Workplane("XY")
           .box(width, thickness, lip_height, centered=(True, True, False))
           .translate((0, -depth / 2 + thickness / 2, thickness)))
    # Back support angled
    back_h = depth * math.tan(math.radians(angle - 45)) + 10
    back = (cq.Workplane("XY")
            .box(width, thickness, back_h, centered=(True, True, False))
            .translate((0, depth / 2 - thickness / 2, thickness)))
    return base.union(lip).union(back)


@register(
    "corner_bracket",
    {
        "description": "90° nurgakinnitus (mööbel, raamid)",
        "params": {
            "arm_length": {"type": "number", "unit": "mm", "min": 20, "max": 100, "default": 50},
            "width": {"type": "number", "unit": "mm", "min": 15, "max": 60, "default": 25},
            "thickness": {"type": "number", "unit": "mm", "min": 3, "max": 10, "default": 5},
            "screw_hole": {"type": "number", "unit": "mm", "min": 3, "max": 8, "default": 5},
        },
    },
)
def corner_bracket(arm_length=50, width=25, thickness=5, screw_hole=5):
    arm1 = cq.Workplane("XY").box(arm_length, width, thickness, centered=(False, True, False))
    arm2 = (cq.Workplane("XY").box(thickness, width, arm_length, centered=(False, True, False))
            .translate((0, 0, 0)))
    body = arm1.union(arm2)
    # Screw holes
    body = (body.faces(">Z").workplane()
            .pushPoints([(arm_length - 12, 0)])
            .hole(screw_hole))
    body = (body.faces(">X").workplane()
            .pushPoints([(0, arm_length - 12)])
            .hole(screw_hole))
    # Gusset/diagonal reinforcement
    gusset = (cq.Workplane("XZ")
              .moveTo(thickness, 0)
              .lineTo(arm_length * 0.5, 0)
              .lineTo(thickness, arm_length * 0.5)
              .close()
              .extrude(width)
              .translate((0, -width / 2, 0)))
    return body.union(gusset)


@register(
    "raspberry_pi_case",
    {
        "description": "Raspberry Pi 4 korpus (ventilatsiooniga)",
        "params": {
            "wall": {"type": "number", "unit": "mm", "min": 1.5, "max": 5, "default": 2},
            "vent_slots": {"type": "number", "unit": "tk", "min": 3, "max": 10, "default": 6},
            "standoff_height": {"type": "number", "unit": "mm", "min": 3, "max": 8, "default": 4},
        },
    },
)
def raspberry_pi_case(wall=2, vent_slots=6, standoff_height=4):
    # Pi 4 dimensions: 85 x 56 x ~20mm board+components
    pcb_w = 85
    pcb_d = 56
    inner_h = 20 + standoff_height
    w = pcb_w + 2 * wall
    d = pcb_d + 2 * wall
    h = inner_h + wall
    # Outer shell
    outer = cq.Workplane("XY").box(w, d, h, centered=(True, True, False))
    inner = (cq.Workplane("XY")
             .box(pcb_w, pcb_d, inner_h, centered=(True, True, False))
             .translate((0, 0, wall)))
    body = outer.cut(inner)
    # PCB standoffs (4 corners, Pi4 hole pattern 58x49mm)
    for dx, dy in [(1, 1), (1, -1), (-1, 1), (-1, -1)]:
        cx = dx * 29
        cy = dy * 24.5
        post = (cq.Workplane("XY").circle(3).extrude(standoff_height)
                .translate((cx, cy, wall)))
        post = post.faces(">Z").workplane().hole(2.75, depth=standoff_height)
        body = body.union(post)
    # Ventilation slots on top
    n = int(vent_slots)
    slot_w = (pcb_w - 10) / (2 * n)
    for i in range(n):
        x = -pcb_w / 2 + 10 + i * (slot_w + slot_w)
        vent = (cq.Workplane("XY")
                .box(slot_w, pcb_d * 0.6, wall + 2, centered=(False, True, False))
                .translate((x, 0, h - wall - 1)))
        body = body.cut(vent)
    # USB/HDMI port cutouts (simplified: two large openings on one side)
    port_cut = (cq.Workplane("XY")
                .box(pcb_w * 0.6, wall + 2, 12, centered=(True, True, False))
                .translate((0, -d / 2, wall + standoff_height + 2)))
    body = body.cut(port_cut)
    return body


@register(
    "pot_planter",
    {
        "description": "Kooniline lillepott drenaaziaukudega (top/bottom diameter, drain_holes)",
        "params": {
            "top_diameter":    {"type": "number", "unit": "mm", "min": 40, "max": 300, "default": 100},
            "bottom_diameter": {"type": "number", "unit": "mm", "min": 30, "max": 280, "default": 80},
            "height":          {"type": "number", "unit": "mm", "min": 30, "max": 300, "default": 120},
            "wall":            {"type": "number", "unit": "mm", "min": 1.5, "max": 5, "default": 2.5},
            "drain_holes":     {"type": "number", "unit": "tk", "min": 0, "max": 8, "default": 4},
            "drain_diameter":  {"type": "number", "unit": "mm", "min": 3, "max": 15, "default": 6},
        },
    },
)
def pot_planter(top_diameter=100, bottom_diameter=80, height=120, wall=2.5,
                drain_holes=4, drain_diameter=6):
    """
    Kooniline lillepott (frustum) õõnsa sisemuse ja põhjas paiknevate
    drenaaziaukudega. Kasutab `loft`-i sujuva koonuse saavutamiseks ning
    cut'ib põhja keskele + ümber keskme ringikujulise pattern'iga augud.

    Ohutuspiirangud:
    - bottom_diameter peab olema vähemalt 2*wall väiksem kui top_diameter,
      et sein kuskil ei kuluks null-paksuseks (clamp'ime seest sees).
    - drain_holes==0 lubatud (veetaim/akvaariumi-kasutus).
    """
    t = max(1.5, float(wall))
    d_top = float(top_diameter)
    d_bot = float(bottom_diameter)
    h = float(height)

    # Väline koonus (frustum) loft'iga alt-üles
    outer = (
        cq.Workplane("XY").circle(d_bot / 2).workplane(offset=h).circle(d_top / 2)
        .loft(combine=True)
    )

    # Sisemine koonus — samad proportsioonid aga seina paksus võrra väiksem.
    # Sisemuse põhi on `t` võrra kõrgemal (põhja paksus) ja ulatub kuni
    # ülaservast veidi alla (servatäis et loft ei plahvataks).
    inner_bot_r = max(1.0, d_bot / 2 - t)
    inner_top_r = max(1.0, d_top / 2 - t)
    inner = (
        cq.Workplane("XY").workplane(offset=t).circle(inner_bot_r)
        .workplane(offset=h - t - 0.01).circle(inner_top_r)
        .loft(combine=True)
    )
    body = outer.cut(inner)

    # Drenaaziaugud põhjas — üks keskel + (N-1) ümber keskme ringis.
    n = int(drain_holes)
    if n > 0:
        hole_r = float(drain_diameter) / 2
        # Keskmine auk
        center_cut = (
            cq.Workplane("XY").circle(hole_r).extrude(t + 1)
            .translate((0, 0, -0.5))
        )
        body = body.cut(center_cut)
        # Ülejäänud ringis — raadius kuni 40% põhja raadiusest
        if n > 1:
            import math
            ring_r = max(hole_r * 2 + 2, 0.4 * (d_bot / 2 - t))
            # Kui ring_r liiga suur põhja jaoks, clamp
            ring_r = min(ring_r, d_bot / 2 - t - hole_r - 1)
            if ring_r > 0:
                for i in range(n - 1):
                    angle = 2 * math.pi * i / (n - 1)
                    x = ring_r * math.cos(angle)
                    y = ring_r * math.sin(angle)
                    cut = (
                        cq.Workplane("XY").circle(hole_r).extrude(t + 1)
                        .translate((x, y, -0.5))
                    )
                    body = body.cut(cut)

    return body


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


@app.post("/metrics")
def metrics(req: GenRequest):
    """
    Non-binary endpoint: compute volume, bounding box, rough print-time and weight.
    Lets the frontend preview "how real is this" before paying the STL download.
    """
    tpl = TEMPLATES.get(req.template)
    if not tpl:
        raise HTTPException(404, f"Unknown template: {req.template}")
    try:
        model = tpl["fn"](**req.params)
    except TypeError as e:
        raise HTTPException(400, f"Bad params: {e}")

    solid = model.val() if hasattr(model, "val") else model
    vol_mm3 = float(solid.Volume())
    bb = solid.BoundingBox()
    size = (bb.xlen, bb.ylen, bb.zlen)

    # Heuristics — rough but useful for the user
    pla_density_g_per_cm3 = 1.24
    weight_g = (vol_mm3 / 1000.0) * pla_density_g_per_cm3
    # 0.2mm layers, 60mm/s, 20% infill — rough print-time estimate
    volume_cm3 = vol_mm3 / 1000.0
    print_min = max(5, round(volume_cm3 * 3.5))

    # Simple overhang warning: tall + narrow bbox
    overhang_risk = size[2] > 2 * max(size[0], size[1]) and min(size[0], size[1]) < 15

    return {
        "volume_mm3": round(vol_mm3, 1),
        "volume_cm3": round(volume_cm3, 2),
        "bbox_mm": {"x": round(size[0], 1), "y": round(size[1], 1), "z": round(size[2], 1)},
        "weight_g_pla": round(weight_g, 1),
        "print_time_min_estimate": print_min,
        "overhang_risk": overhang_risk,
    }


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


@app.post("/generate_step")
def generate_step(req: GenRequest):
    """
    STEP eksport — sama mis /generate, aga väljund on ISO 10303-21 STEP-fail
    (mitte STL). STEP on parameetriline B-Rep vormingus fail, mida inseneri-
    tasandi CAD-tarkvara (Fusion 360, SolidWorks, Onshape, FreeCAD) saab avada
    ja EDASI muuta. STL on pelgalt kolmnurkade hulk — ei saa muuta.

    See endpoint lisab sinu toote insener-kasutajaskonnale väärtust, mida STL-i
    väljund ei paku. Lisatud 2026-04 strateegia-audit raporti soovituse järgi.
    """
    tpl = TEMPLATES.get(req.template)
    if not tpl:
        raise HTTPException(404, f"Unknown template: {req.template}")
    try:
        model = tpl["fn"](**req.params)
    except TypeError as e:
        raise HTTPException(400, f"Bad params: {e}")

    with tempfile.NamedTemporaryFile(suffix=".step", delete=False) as f:
        path = f.name
    try:
        cq.exporters.export(model, path)   # laiendist tuletab vormi
        with open(path, "rb") as fh:
            data = fh.read()
    finally:
        os.unlink(path)

    return Response(
        content=data,
        media_type="application/step",
        headers={"Content-Disposition": f'attachment; filename="{req.template}.step"'},
    )


# --- Darwin CAD ja Freeform script-gen -------------------------------------
# Need paigaldame pärast TEMPLATES sõnastiku ja /generate määratlust, et
# moodulid saaksid ligi.
_register_evolve(app, TEMPLATES)
_register_freeform(app)
_register_printflow(app)
_register_dfm(app, TEMPLATES)
