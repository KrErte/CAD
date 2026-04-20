"""
Testid PrintFlow DFM ja nesting-moodulile. Kasutab cadquery'it, et luua
lihtne kuubik/silinder STL-failina, ja siis saadab selle trimeshile
analüüsimiseks.
"""
import io
import os
import tempfile

import pytest
from fastapi.testclient import TestClient

try:
    import trimesh
    import numpy as np
    _HAS_TRIMESH = True
except ImportError:
    _HAS_TRIMESH = False

try:
    import rectpack
    _HAS_RECTPACK = True
except ImportError:
    _HAS_RECTPACK = False

# Impordime app.py kaudu, mis registerib /dfm ja /nest
from app import app

client = TestClient(app)


@pytest.mark.skipif(not _HAS_TRIMESH, reason="trimesh pole installed")
def test_dfm_simple_cube():
    """Võtame cadquery'ga 20mm kuubi, saadame /dfm-le, kontrollime et
    ruumala on ~8 cm3 ja severity == OK."""
    import cadquery as cq

    cube = cq.Workplane("XY").box(20, 20, 20, centered=(True, True, False))
    with tempfile.NamedTemporaryFile(suffix=".stl", delete=False) as f:
        path = f.name
    try:
        cq.exporters.export(cube, path)
        with open(path, "rb") as fh:
            r = client.post(
                "/dfm",
                files={"file": ("cube.stl", fh, "application/sla")},
                data={
                    "material_density_g_cm3": "1.24",
                    "price_per_kg_eur": "25",
                    "min_wall_mm": "0.8",
                    "max_overhang_deg": "45",
                    "build_volume_x": "256",
                    "build_volume_y": "256",
                    "build_volume_z": "256",
                },
            )
        assert r.status_code == 200
        data = r.json()
        # 20*20*20 = 8000 mm3 = 8 cm3 (tolerantsiga triangulation-veale)
        assert 7.5 <= data["volume_cm3"] <= 8.5
        assert data["severity"] == "OK"
        assert data["ok"] is True
        # bbox peaks olema ~20mm iga telje peal
        assert abs(data["bbox_mm"]["x"] - 20) < 0.5
        assert abs(data["bbox_mm"]["z"] - 20) < 0.5
    finally:
        os.unlink(path)


@pytest.mark.skipif(not _HAS_TRIMESH, reason="trimesh pole installed")
def test_dfm_blocks_oversized_part():
    """Suur 500mm kuubik peaks saama BBOX_OVER BLOCK warning'u kui
    build volume on 256mm."""
    import cadquery as cq

    big = cq.Workplane("XY").box(500, 100, 100, centered=(True, True, False))
    with tempfile.NamedTemporaryFile(suffix=".stl", delete=False) as f:
        path = f.name
    try:
        cq.exporters.export(big, path)
        with open(path, "rb") as fh:
            r = client.post(
                "/dfm",
                files={"file": ("big.stl", fh, "application/sla")},
                data={
                    "build_volume_x": "256",
                    "build_volume_y": "256",
                    "build_volume_z": "256",
                },
            )
        data = r.json()
        assert data["severity"] == "BLOCK"
        assert data["ok"] is False
        codes = [i["code"] for i in data["issues"]]
        assert "BBOX_OVER" in codes
    finally:
        os.unlink(path)


@pytest.mark.skipif(not _HAS_RECTPACK, reason="rectpack pole installed")
def test_nest_fits_parts_in_single_plate():
    """4 × 50×50 osa peaks mahtuma 256×256 plaadile ühel plaadil."""
    r = client.post("/nest", json={
        "parts": [
            {"id": 1, "w_mm": 50, "h_mm": 50, "qty": 4},
        ],
        "plate_w_mm": 256,
        "plate_h_mm": 256,
        "margin_mm": 3,
    })
    assert r.status_code == 200
    data = r.json()
    assert data["plates_used"] == 1
    assert len(data["placements"]) == 4
    assert data["utilization_pct"] > 10  # 4 * 50*50 = 10_000, plaat = 65_536


@pytest.mark.skipif(not _HAS_RECTPACK, reason="rectpack pole installed")
def test_nest_overflow_uses_multiple_plates():
    """100 × 50×50 osa ei mahu ühele 256×256 plaadile — vajame vähemalt 4."""
    r = client.post("/nest", json={
        "parts": [{"id": 1, "w_mm": 50, "h_mm": 50, "qty": 100}],
        "plate_w_mm": 256,
        "plate_h_mm": 256,
        "margin_mm": 3,
    })
    data = r.json()
    # 256/53 ≈ 4 per rida, 4*4 = 16 per plaat, 100/16 ≈ 6-7 plaati
    assert data["plates_used"] >= 5


@pytest.mark.skipif(not _HAS_TRIMESH, reason="trimesh pole installed")
def test_slice_meta_returns_fast_heuristic():
    import cadquery as cq
    cube = cq.Workplane("XY").box(30, 30, 30, centered=(True, True, False))
    with tempfile.NamedTemporaryFile(suffix=".stl", delete=False) as f:
        path = f.name
    try:
        cq.exporters.export(cube, path)
        with open(path, "rb") as fh:
            r = client.post("/slice-meta", files={"file": ("c.stl", fh, "application/sla")})
        data = r.json()
        assert data["volume_cm3"] > 20
        assert data["print_time_min"] > 0
        assert "bbox_mm" in data
    finally:
        os.unlink(path)
