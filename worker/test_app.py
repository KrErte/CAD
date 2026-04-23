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


def test_pot_planter_in_catalog():
    """pot_planter peab olema registreeritud ja korrektse skeemiga."""
    r = client.get("/templates")
    assert r.status_code == 200
    data = r.json()
    assert "pot_planter" in data
    params = data["pot_planter"]["params"]
    for key in ("top_diameter", "bottom_diameter", "height", "wall",
               "drain_holes", "drain_diameter"):
        assert key in params, f"pot_planter missing param {key}"
        assert "min" in params[key] and "max" in params[key]
        assert params[key]["min"] < params[key]["max"]


def test_pot_planter_without_drain_holes():
    """drain_holes=0 peab olema lubatud (nt akvaariumi-taim)."""
    r = client.post("/generate", json={
        "template": "pot_planter",
        "params": {
            "top_diameter": 100, "bottom_diameter": 80, "height": 120,
            "wall": 2.5, "drain_holes": 0, "drain_diameter": 6,
        },
    })
    assert r.status_code == 200
    assert len(r.content) > 100


def test_pot_planter_max_drain_holes():
    """8 auguga peab samuti STL'i andma."""
    r = client.post("/generate", json={
        "template": "pot_planter",
        "params": {
            "top_diameter": 150, "bottom_diameter": 120, "height": 150,
            "wall": 3, "drain_holes": 8, "drain_diameter": 8,
        },
    })
    assert r.status_code == 200
    assert len(r.content) > 100


def test_pot_planter_metrics_endpoint():
    """metrics peab andma mõistlikud mahud ja massi."""
    r = client.post("/metrics", json={
        "template": "pot_planter",
        "params": {
            "top_diameter": 100, "bottom_diameter": 80, "height": 120,
            "wall": 2.5, "drain_holes": 4, "drain_diameter": 6,
        },
    })
    assert r.status_code == 200
    body = r.json()
    assert body["volume_mm3"] > 0
    assert body["weight_g_pla"] > 0
    # pott ~100mm kõrgune — bbox z peab olema ligikaudu selles piires
    assert 100 < body["bbox_mm"]["z"] < 140
