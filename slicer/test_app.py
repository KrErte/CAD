"""
Tests for the slicer sidecar.

We unit-test the gcode header parser against fixtures and integration-test
/slice with a monkey-patched subprocess call — this way CI doesn't need the
PrusaSlicer binary to be installed just to verify the HTTP surface stays
backwards compatible.
"""
from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import app as slicer_app
from app import app, parse_gcode_header, _human_duration, _parse_duration, _TIME_RE

FIX = Path(__file__).parent / "tests" / "fixtures"


client = TestClient(app)


# --- parser unit tests ------------------------------------------------------

def test_parse_gcode_header_small_print():
    stats = parse_gcode_header(FIX / "sample_header.gcode")
    assert stats["print_time_sec"] == 23 * 60 + 17
    assert stats["filament_length_mm"] == pytest.approx(2487.35)
    assert stats["filament_volume_cm3"] == pytest.approx(5.98)
    assert stats["filament_g"] == pytest.approx(7.42)
    assert stats["filament_cost_eur"] == pytest.approx(0.19)


def test_parse_gcode_header_multi_day_print():
    stats = parse_gcode_header(FIX / "big_print_header.gcode")
    # 1d 3h 14m 22s = (27h 14m 22s) = 98062s
    assert stats["print_time_sec"] == ((1 * 24 + 3) * 60 + 14) * 60 + 22
    assert stats["filament_g"] == pytest.approx(242.06)


def test_parse_duration_regex_handles_missing_fields():
    assert _parse_duration(_TIME_RE.search("estimated printing time (normal mode) = 45s")) == 45
    assert _parse_duration(_TIME_RE.search("estimated printing time (normal mode) = 2h")) == 7200
    assert _parse_duration(_TIME_RE.search("estimated printing time (normal mode) = 0h 0m")) is None


def test_parse_gcode_header_rejects_garbage(tmp_path):
    bad = tmp_path / "garbage.gcode"
    bad.write_text("; not a prusa slicer gcode\nG1 X0 Y0\n")
    with pytest.raises(ValueError):
        parse_gcode_header(bad)


def test_human_duration_formats():
    assert _human_duration(None) == "?"
    assert _human_duration(45) == "45s"
    assert _human_duration(125) == "2min 5s"
    assert _human_duration(3670) == "1h 1min"


# --- http surface tests -----------------------------------------------------

def test_health_lists_presets():
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert "pla_default" in body["presets"]


def test_presets_endpoint():
    r = client.get("/presets")
    assert r.status_code == 200
    body = r.json()
    assert body["default"] == "pla_default"
    assert "pla_default" in body["presets"]
    assert "petg_default" in body["presets"]


def test_slice_rejects_empty_upload():
    r = client.post(
        "/slice",
        files={"stl": ("empty.stl", b"", "application/sla")},
        data={"preset": "pla_default"},
    )
    assert r.status_code == 400


def test_slice_rejects_unknown_preset():
    # Patch the subprocess runner so the test fails fast on the preset lookup
    # rather than trying to shell out.
    r = client.post(
        "/slice",
        files={"stl": ("cube.stl", b"solid cube\nendsolid cube\n", "model/stl")},
        data={"preset": "does_not_exist"},
    )
    assert r.status_code == 404


def test_slice_happy_path_with_mocked_slicer(monkeypatch, tmp_path):
    """
    End-to-end test that verifies: request → tempdir setup → slicer call →
    gcode parsing → JSON response shape — WITHOUT needing PrusaSlicer installed.
    We stub `_run_prusa_slicer` to drop our fixture gcode at the expected path.
    """
    fixture = (FIX / "sample_header.gcode").read_bytes()

    async def fake_run(stl_path, cfg_path, out_gcode):
        out_gcode.write_bytes(fixture)
        return "fake slicer log"

    monkeypatch.setattr(slicer_app, "_run_prusa_slicer", fake_run)

    r = client.post(
        "/slice",
        files={"stl": ("cube.stl", b"solid cube\nendsolid cube\n", "model/stl")},
        data={"preset": "pla_default", "fill_density": "15"},
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["preset"] == "pla_default"
    assert body["overrides"] == {"fill_density": "15"}
    assert body["print_time_sec"] == 23 * 60 + 17
    assert body["print_time_human"].startswith("23min")
    assert body["filament_g"] == pytest.approx(7.4, abs=0.2)
    assert body["filament_cost_eur"] == pytest.approx(0.19)
    assert body["filament_length_m"] == pytest.approx(2.49, abs=0.02)
