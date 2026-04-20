"""Smoke tests — every template must produce a non-empty STL with valid params."""
from fastapi.testclient import TestClient
from app import app, TEMPLATES

client = TestClient(app)


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_templates_listed():
    r = client.get("/templates")
    assert r.status_code == 200
    data = r.json()
    for name in ["shelf_bracket", "hook", "box", "adapter", "cable_clamp", "tag"]:
        assert name in data
        assert "params" in data[name]


def test_each_template_generates_with_defaults():
    for name, tpl in TEMPLATES.items():
        defaults = {k: v["default"] for k, v in tpl["schema"]["params"].items()}
        r = client.post("/generate", json={"template": name, "params": defaults})
        assert r.status_code == 200, f"{name} failed: {r.text}"
        # STL files always start with "solid" (ASCII) or 80-byte header (binary)
        assert len(r.content) > 100, f"{name} produced suspiciously small STL"


def test_unknown_template_404():
    r = client.post("/generate", json={"template": "nope", "params": {}})
    assert r.status_code == 404


def test_bad_params_400():
    r = client.post("/generate", json={"template": "box", "params": {"banana": 1}})
    assert r.status_code == 400


# ---------------------------------------------------------------------------
# BUG-FIX: Päris geomeetria integratsioonitestid — eelmised testid kontrollisid
# ainult HTTP staatuskoode ja STL suurust, MITTE tegelikku geomeetriat.
# Iga template-kategooria kohta vähemalt üks test, mis kontrollib mõõtmeid,
# mahtu ja bounding box'i.
# ---------------------------------------------------------------------------

import cadquery as cq

def _build_and_measure(template_name, params):
    """Abifunktsioon: ehitab CadQuery mudeli ja tagastab mõõdud."""
    tpl = TEMPLATES[template_name]
    model = tpl["fn"](**params)
    solid = model.val() if hasattr(model, "val") else model
    bb = solid.BoundingBox()
    return {
        "volume_mm3": float(solid.Volume()),
        "xlen": bb.xlen,
        "ylen": bb.ylen,
        "zlen": bb.zlen,
    }


def test_box_geometry_matches_params():
    """Karp peab olema ligikaudu pikkus×laius×kõrgus (miinus seinapaksus)."""
    m = _build_and_measure("box", {"length": 80, "width": 60, "height": 40, "wall": 2.5})
    # Bounding box peab vastama välismõõtmetele
    assert abs(m["xlen"] - 80) < 1, f"box x={m['xlen']}, oodati ~80"
    assert abs(m["ylen"] - 60) < 1, f"box y={m['ylen']}, oodati ~60"
    assert abs(m["zlen"] - 40) < 1, f"box z={m['zlen']}, oodati ~40"
    # Maht peab olema väiksem kui täiskuup (karp on sees õõnes)
    full_volume = 80 * 60 * 40
    assert m["volume_mm3"] < full_volume, "Karp peaks olema sees õõnes"
    assert m["volume_mm3"] > 0, "Maht peab olema positiivne"


def test_shelf_bracket_geometry():
    """Riiuliklamber peab olema suurem kui toru diameeter."""
    m = _build_and_measure("shelf_bracket", {
        "pipe_diameter": 32, "load_kg": 5, "arm_length": 120, "wall_thickness": 5
    })
    # Mudel peab olema vähemalt nii lai kui arm_length + toru raadius
    assert m["xlen"] > 120, f"Bracket x={m['xlen']}, peaks olema > 120 (arm_length)"
    assert m["volume_mm3"] > 0


def test_hook_load_scaling():
    """Suurem koormus → paksem konks (automaatne skaleerimine)."""
    m_light = _build_and_measure("hook", {"load_kg": 1, "reach": 50})
    m_heavy = _build_and_measure("hook", {"load_kg": 15, "reach": 50})
    # Raskem konks peaks olema suurema mahuga (paksem materjal)
    assert m_heavy["volume_mm3"] > m_light["volume_mm3"], \
        "Raskem konks peaks olema suurema mahuga"


def test_adapter_has_hole():
    """Adapter peab olema toruline (maht < täissilindri maht)."""
    import math
    m = _build_and_measure("adapter", {"d_in": 25, "d_out": 32, "length": 40, "wall": 2.5})
    # Maksimaalne maht oleks täissilinder d_out+wall diameetriga
    max_vol = math.pi * ((32/2 + 2.5) ** 2) * 40
    assert m["volume_mm3"] < max_vol, "Adapter peab olema sees õõnes"
    assert m["volume_mm3"] > 0


def test_spur_gear_teeth_count():
    """Hammasratta bounding box peab skaleeruma hammaste arvuga."""
    m_small = _build_and_measure("spur_gear", {"module": 2, "teeth": 10, "thickness": 8, "bore": 5})
    m_large = _build_and_measure("spur_gear", {"module": 2, "teeth": 30, "thickness": 8, "bore": 5})
    # Suurem hammaste arv → suurem diameeter
    assert m_large["xlen"] > m_small["xlen"], \
        "30-hambaline ratas peab olema suurem kui 10-hambaline"


def test_metrics_endpoint_returns_valid_data():
    """Metrics endpoint peab tagastama mõistlikud numbrilised väärtused."""
    r = client.post("/metrics", json={
        "template": "box",
        "params": {"length": 80, "width": 60, "height": 40, "wall": 2.5}
    })
    assert r.status_code == 200
    data = r.json()
    assert data["volume_mm3"] > 0
    assert data["weight_g_pla"] > 0
    assert data["print_time_min_estimate"] >= 5
    assert "bbox_mm" in data
    assert all(k in data["bbox_mm"] for k in ["x", "y", "z"])
