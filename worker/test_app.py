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
