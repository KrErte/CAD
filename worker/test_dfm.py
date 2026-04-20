"""
Pytest suite reeglipõhise DFM-analüsaatori jaoks.

Iga reegli test:
  1. "hea" case — reegel EI peaks firemant
  2. "halb" case — reegel PEAB fireima
  3. severity peab olema õige kategooria
  4. suggested_value peab olema clamp'ibel number

Lisaks integration-test /dfm endpoint'i läbi TestClient'i.
"""
import pytest
from fastapi.testclient import TestClient

from app import app
from dfm import analyze, rule_thin_wall, rule_overhang, rule_min_feature, rule_bridge

client = TestClient(app)


# ---------------------------------------------------------------------------
# rule_thin_wall
# ---------------------------------------------------------------------------

class TestThinWall:
    def test_thick_enough_no_issue(self):
        issues = rule_thin_wall("box", {"wall": 3.0})
        assert not any(i.rule == "thin_wall" for i in issues)

    def test_below_min_critical(self):
        issues = rule_thin_wall("box", {"wall": 0.8})
        thin = [i for i in issues if i.rule == "thin_wall"]
        assert len(thin) == 1
        assert thin[0].severity == "critical"
        assert thin[0].suggested_value == 2.0

    def test_below_ideal_warning(self):
        issues = rule_thin_wall("box", {"wall": 1.5})
        thin = [i for i in issues if i.rule == "thin_wall"]
        assert len(thin) == 1
        assert thin[0].severity == "warning"

    def test_load_vs_wall_mismatch(self):
        # 10kg koormus, 3mm sein — alla soovituse 3 + 0.4*10 = 7mm
        issues = rule_thin_wall("shelf_bracket", {"wall_thickness": 3.0, "load_kg": 10})
        load_issues = [i for i in issues if i.rule == "load_vs_wall"]
        assert len(load_issues) == 1
        assert load_issues[0].suggested_value >= 7.0

    def test_load_vs_wall_ok(self):
        # 5kg koormus, 5mm sein — sobib (3 + 0.4*5 = 5mm)
        issues = rule_thin_wall("shelf_bracket", {"wall_thickness": 5.0, "load_kg": 5})
        load_issues = [i for i in issues if i.rule == "load_vs_wall"]
        assert len(load_issues) == 0


# ---------------------------------------------------------------------------
# rule_overhang
# ---------------------------------------------------------------------------

class TestOverhang:
    def test_hook_long_reach_high_load_warns(self):
        issues = rule_overhang("hook", {"reach": 70, "load_kg": 6})
        overhang = [i for i in issues if i.rule == "overhang"]
        assert len(overhang) == 1
        assert overhang[0].severity == "warning"

    def test_hook_small_no_issue(self):
        issues = rule_overhang("hook", {"reach": 40, "load_kg": 3})
        assert not any(i.rule == "overhang" for i in issues)

    def test_lever_arm_ratio_warns(self):
        issues = rule_overhang("shelf_bracket", {"arm_length": 200, "pipe_diameter": 30})
        lever = [i for i in issues if i.rule == "lever_arm"]
        assert len(lever) == 1

    def test_lever_arm_ok(self):
        issues = rule_overhang("shelf_bracket", {"arm_length": 80, "pipe_diameter": 30})
        lever = [i for i in issues if i.rule == "lever_arm"]
        assert len(lever) == 0

    def test_hinge_too_few_cuts(self):
        issues = rule_overhang("living_hinge", {"hinge_cuts": 3})
        cuts = [i for i in issues if i.rule == "hinge_cuts"]
        assert len(cuts) == 1


# ---------------------------------------------------------------------------
# rule_min_feature
# ---------------------------------------------------------------------------

class TestMinFeature:
    def test_tiny_hole_warns(self):
        issues = rule_min_feature("box", {"screw_hole": 1.2})
        assert any(i.rule == "min_hole" for i in issues)

    def test_normal_hole_ok(self):
        issues = rule_min_feature("box", {"screw_hole": 3.0})
        assert not any(i.rule == "min_hole" for i in issues)

    def test_tiny_gear_module_critical(self):
        issues = rule_min_feature("spur_gear", {"module": 0.5})
        gear = [i for i in issues if i.rule == "gear_tooth"]
        assert len(gear) == 1
        assert gear[0].severity == "critical"

    def test_snap_too_thin_critical(self):
        issues = rule_min_feature("snap_fit_clip", {"thickness": 0.5})
        snap = [i for i in issues if i.rule == "snap_thickness"]
        assert len(snap) == 1
        assert snap[0].severity == "critical"


# ---------------------------------------------------------------------------
# rule_bridge
# ---------------------------------------------------------------------------

class TestBridge:
    def test_wide_box_info(self):
        issues = rule_bridge("box", {"width": 100, "depth": 100})
        assert any(i.rule == "bridge" for i in issues)

    def test_small_box_ok(self):
        issues = rule_bridge("box", {"width": 30, "depth": 30})
        assert not any(i.rule == "bridge" for i in issues)


# ---------------------------------------------------------------------------
# analyze() — aggregate
# ---------------------------------------------------------------------------

class TestAnalyze:
    def test_clean_design_score_10(self):
        out = analyze("box", {"length": 80, "width": 60, "height": 40, "wall": 3.0})
        assert out["score"] == 10.0
        assert out["counts"]["critical"] == 0
        assert "puhas" in out["summary_et"].lower()

    def test_critical_drops_score(self):
        # wall 0.5 → critical (3.0 penalty)
        out = analyze("box", {"wall": 0.5})
        assert out["score"] < 10.0
        assert out["counts"]["critical"] >= 1

    def test_multi_issues_clamps_min(self):
        # Ekstreemsed: palju kriitilisi → score ei lähe alla 1
        out = analyze("shelf_bracket", {
            "wall_thickness": 0.3,
            "load_kg": 30,
            "arm_length": 200,
            "pipe_diameter": 10,
        })
        assert 1.0 <= out["score"] <= 10.0

    def test_issues_list_shape(self):
        out = analyze("hook", {"reach": 80, "load_kg": 8})
        for i in out["issues"]:
            assert "severity" in i
            assert "rule" in i
            assert "message_et" in i


# ---------------------------------------------------------------------------
# Endpoint integration
# ---------------------------------------------------------------------------

class TestEndpoint:
    def test_dfm_endpoint_200(self):
        r = client.post("/dfm", json={
            "template": "box",
            "params": {"length": 80, "width": 60, "height": 40, "wall": 2.5},
        })
        assert r.status_code == 200
        body = r.json()
        assert body["template"] == "box"
        assert "score" in body
        assert "issues" in body

    def test_dfm_unknown_template_404(self):
        r = client.post("/dfm", json={"template": "nonexistent_thing", "params": {}})
        assert r.status_code == 404

    def test_dfm_empty_params_no_crash(self):
        r = client.post("/dfm", json={"template": "box", "params": {}})
        assert r.status_code == 200
