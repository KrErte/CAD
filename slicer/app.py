"""
Slicer sidecar — wraps PrusaSlicer CLI so the backend can estimate
print-time, filament mass and cost before the user pays for the STL download.

Flow:
  Backend POSTs STL bytes to /slice
    ↓
  We write STL to a tempfile, shell out to `prusa-slicer --export-gcode ...`
  (using a bundled PLA/PETG preset), parse the resulting gcode header comments
  for printing time + filament usage, and return a small JSON.

PrusaSlicer emits the following relevant comments into the gcode header:
    ; estimated printing time (normal mode) = 1h 23m 45s
    ; filament used [mm]  = 12345.67
    ; filament used [cm3] = 29.87
    ; filament used [g]   = 37.04
    ; filament cost       = 0.86

`filament cost` is "per unit" in the preset's currency — we treat it as EUR
here and let the caller override filament_cost / filament_density / etc.
"""
from __future__ import annotations

import asyncio
import os
import re
import shutil
import tempfile
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from pydantic import BaseModel

app = FastAPI(title="AI-CAD Slicer", version="0.1.0")

PROFILES_DIR = Path(__file__).parent / "profiles"
SLICER_BIN_ENV = os.environ.get("SLICER_BIN", "xvfb-run -a prusa-slicer")
SLICER_TIMEOUT_SEC = int(os.environ.get("SLICER_TIMEOUT_SEC", "120"))
# EUR exchange / default spool cost — allows an ops override without rebuilding.
DEFAULT_FILAMENT_COST_EUR = float(os.environ.get("DEFAULT_FILAMENT_COST_EUR", "25"))


# --- Gcode header parsing ---------------------------------------------------

_TIME_RE = re.compile(
    r"estimated printing time \(normal mode\)\s*=\s*"
    r"(?:(?P<d>\d+)d\s*)?"
    r"(?:(?P<h>\d+)h\s*)?"
    r"(?:(?P<m>\d+)m\s*)?"
    r"(?:(?P<s>\d+)s)?",
    re.IGNORECASE,
)
_FIL_MM_RE = re.compile(r"filament used\s*\[mm\]\s*=\s*([\d.]+)", re.IGNORECASE)
_FIL_CM3_RE = re.compile(r"filament used\s*\[cm3\]\s*=\s*([\d.]+)", re.IGNORECASE)
_FIL_G_RE = re.compile(r"filament used\s*\[g\]\s*=\s*([\d.]+)", re.IGNORECASE)
_FIL_COST_RE = re.compile(r"filament cost\s*=\s*([\d.]+)", re.IGNORECASE)


def _parse_duration(match: re.Match | None) -> int | None:
    if not match:
        return None
    d = int(match.group("d") or 0)
    h = int(match.group("h") or 0)
    m = int(match.group("m") or 0)
    s = int(match.group("s") or 0)
    total = ((d * 24 + h) * 60 + m) * 60 + s
    return total if total > 0 else None


def parse_gcode_header(gcode_path: Path) -> dict[str, Any]:
    """
    Read the first ~200 KB of the gcode (PrusaSlicer emits its stats block
    in the first few KB of header comments; 200 KB is safe for large models).
    """
    with gcode_path.open("rb") as fh:
        blob = fh.read(200_000).decode("utf-8", errors="ignore")

    print_time_sec = _parse_duration(_TIME_RE.search(blob))
    fil_mm = _match_float(_FIL_MM_RE, blob)
    fil_cm3 = _match_float(_FIL_CM3_RE, blob)
    fil_g = _match_float(_FIL_G_RE, blob)
    fil_cost = _match_float(_FIL_COST_RE, blob)

    if print_time_sec is None or fil_g is None:
        raise ValueError(
            "PrusaSlicer did not emit the expected stats header. Is the preset "
            "corrupt, or the model too small to slice?"
        )

    return {
        "print_time_sec": print_time_sec,
        "filament_length_mm": fil_mm,
        "filament_volume_cm3": fil_cm3,
        "filament_g": fil_g,
        "filament_cost_eur": fil_cost,
    }


def _match_float(regex: re.Pattern, blob: str) -> float | None:
    m = regex.search(blob)
    return float(m.group(1)) if m else None


# --- Slicer invocation ------------------------------------------------------

def _profile_path(name: str) -> Path:
    p = PROFILES_DIR / f"{name}.ini"
    if not p.is_file():
        raise HTTPException(404, f"Unknown preset: {name}")
    return p


def _build_config(preset: str, overrides: dict[str, Any]) -> Path:
    """
    Start from a bundled preset INI and layer caller overrides on top.
    Returns a path to a merged INI in a fresh tempdir; caller cleans up.
    """
    base_cfg = _profile_path(preset).read_text(encoding="utf-8")

    if overrides:
        extra_lines = ["", "# --- runtime overrides ---"]
        for k, v in overrides.items():
            # PrusaSlicer expects percentages as e.g. "25%"; accept numbers too.
            if k == "fill_density" and isinstance(v, (int, float)):
                v = f"{v}%"
            extra_lines.append(f"{k} = {v}")
        merged = base_cfg + "\n" + "\n".join(extra_lines) + "\n"
    else:
        merged = base_cfg

    tmp = Path(tempfile.mkdtemp(prefix="slicer-cfg-"))
    cfg_path = tmp / "print.ini"
    cfg_path.write_text(merged, encoding="utf-8")
    return cfg_path


async def _run_prusa_slicer(stl: Path, cfg: Path, out_gcode: Path) -> str:
    """
    Shell out to PrusaSlicer. Returns combined stdout/stderr for debugging.
    """
    cmd = (
        f"{SLICER_BIN_ENV} --export-gcode "
        f"--load {cfg.as_posix()} "
        f"--output {out_gcode.as_posix()} "
        f"{stl.as_posix()}"
    )
    proc = await asyncio.create_subprocess_shell(
        cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )
    try:
        out, _ = await asyncio.wait_for(proc.communicate(), timeout=SLICER_TIMEOUT_SEC)
    except asyncio.TimeoutError:
        proc.kill()
        await proc.wait()
        raise HTTPException(504, f"PrusaSlicer timed out after {SLICER_TIMEOUT_SEC}s")

    text = (out or b"").decode("utf-8", errors="ignore")
    if proc.returncode != 0:
        raise HTTPException(500, f"PrusaSlicer failed (rc={proc.returncode}): {text[-800:]}")
    if not out_gcode.exists() or out_gcode.stat().st_size == 0:
        raise HTTPException(500, f"PrusaSlicer produced no gcode: {text[-800:]}")
    return text


# --- Routes -----------------------------------------------------------------

class PresetList(BaseModel):
    presets: list[str]
    default: str


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "presets": sorted(p.stem for p in PROFILES_DIR.glob("*.ini")),
        "slicer_bin": SLICER_BIN_ENV,
    }


@app.get("/presets", response_model=PresetList)
def list_presets() -> PresetList:
    presets = sorted(p.stem for p in PROFILES_DIR.glob("*.ini"))
    return PresetList(presets=presets, default="pla_default")


@app.post("/slice")
async def slice_stl(
    stl: UploadFile = File(..., description="Binary STL file"),
    preset: str = Form("pla_default"),
    fill_density: str | None = Form(None),
    layer_height: str | None = Form(None),
    filament_cost: str | None = Form(None),
    filament_density: str | None = Form(None),
    filament_diameter: str | None = Form(None),
) -> dict[str, Any]:
    data = await stl.read()
    if not data:
        raise HTTPException(400, "Empty STL upload")
    if len(data) > 50 * 1024 * 1024:
        raise HTTPException(413, "STL too large (50 MB limit)")

    overrides: dict[str, Any] = {}
    for name, val in [
        ("fill_density", fill_density),
        ("layer_height", layer_height),
        ("filament_cost", filament_cost),
        ("filament_density", filament_density),
        ("filament_diameter", filament_diameter),
    ]:
        if val is not None and val != "":
            overrides[name] = val

    work = Path(tempfile.mkdtemp(prefix="slicer-job-"))
    cfg_path: Path | None = None
    try:
        stl_path = work / "input.stl"
        stl_path.write_bytes(data)
        cfg_path = _build_config(preset, overrides)
        gcode_path = work / "out.gcode"

        log = await _run_prusa_slicer(stl_path, cfg_path, gcode_path)
        stats = parse_gcode_header(gcode_path)

        # Fill in cost if PrusaSlicer didn't (e.g. the preset has no cost set).
        if stats.get("filament_cost_eur") is None and stats.get("filament_g") is not None:
            # Back-of-envelope: 1 kg spool at DEFAULT_FILAMENT_COST_EUR.
            stats["filament_cost_eur"] = round(
                stats["filament_g"] / 1000.0 * DEFAULT_FILAMENT_COST_EUR, 2
            )

        return {
            "preset": preset,
            "overrides": overrides,
            "print_time_sec": stats["print_time_sec"],
            "print_time_human": _human_duration(stats["print_time_sec"]),
            "filament_length_m": _safe_round(
                (stats.get("filament_length_mm") or 0) / 1000.0, 2
            ),
            "filament_volume_cm3": _safe_round(stats.get("filament_volume_cm3"), 2),
            "filament_g": _safe_round(stats.get("filament_g"), 1),
            "filament_cost_eur": _safe_round(stats.get("filament_cost_eur"), 2),
            # a short slicer log tail is useful for debugging misbehaving STLs
            "slicer_log_tail": log[-400:] if log else "",
        }
    finally:
        shutil.rmtree(work, ignore_errors=True)
        # _build_config created a sibling tempdir; wipe that too
        if cfg_path is not None and cfg_path.parent.exists() and cfg_path.parent != work:
            shutil.rmtree(cfg_path.parent, ignore_errors=True)


def _human_duration(sec: int | None) -> str:
    if not sec:
        return "?"
    h, rem = divmod(int(sec), 3600)
    m, s = divmod(rem, 60)
    if h:
        return f"{h}h {m}min"
    if m:
        return f"{m}min {s}s"
    return f"{s}s"


def _safe_round(v: float | None, digits: int) -> float | None:
    return round(v, digits) if v is not None else None
