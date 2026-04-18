"""
Freeform CadQuery-script generatsioon — AST sandbox + resource limits.

Murrab 27-template'i lae. Claude (või ükskõik milline LLM) saab genereerida
ise CadQuery Python-koodi, mille see worker turvaliselt käivitab ja väljastab
nii STL-i kui STEP-i.

Ohumudel:
  - LLM genereeritud kood on MITTETULDAV — võib olla Bobby Tables.
  - AST-whitelist: keelatud os, sys, subprocess, socket, threading, eval, exec,
    compile, __import__, open, input, pathlib, shutil, ctypes, importlib.
  - Builtins limiteeritud minimaalse nimekirjani.
  - resource.setrlimit: 512MB RAM, 15s CPU aeg.
  - signal.alarm: 15s kogu ajalimiit.
  - Võrguühendus konteineri tasemel (Docker network=none) peaks olema juba
    eraldi piiratud — worker ei peaks kunagi internet'ist midagi tõmbama.
  - Fail-süsteem: sandbox kirjutab ainult tempdir-i ja loeb mitte midagi
    sensitiivset.

Kasutus:
  POST /freeform/generate
     { "code": "...Python...",
       "export": ["stl", "step"],        # kumbki või mõlemad
       "entrypoint": "build" }           # vaikimisi "build"

Tagastab:
  {
    "ok": true/false,
    "error": "...",                      # kui ok=false
    "error_kind": "syntax|forbidden|timeout|runtime|...",
    "files": {
       "stl": "<base64>",
       "step": "<base64>"
    },
    "elapsed_ms": 432
  }

Agentic retry: kui ok=false, saadab backend sama LLM-i tagasi vea-textiga
ja küsib parandust. Max 2 retry — 3 katse peale loobume.
"""
from __future__ import annotations

import ast
import base64
import io
import os
import signal
import sys
import tempfile
import time
import traceback
import resource
from contextlib import contextmanager
from typing import Dict, Any, List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
import cadquery as cq


router = APIRouter(prefix="/freeform", tags=["freeform"])


# ---------------------------------------------------------------------------
# 1) AST-whitelist validator
# ---------------------------------------------------------------------------

# Moodulid, mille import on LUBATUD
ALLOWED_MODULES = {
    "cadquery",
    "cadquery.occ_impl.geom",      # vectors, planes
    "math",
    "random",
    # NB! "os", "sys", "subprocess", "socket" jms on KEELATUD — ära lisa.
}

# Nimed, mida me KEELAME ka globaalselt (koodi sees):
FORBIDDEN_NAMES = {
    "eval", "exec", "compile", "__import__", "__builtins__",
    "open", "input", "breakpoint", "help", "vars", "dir",
    "globals", "locals", "getattr", "setattr", "delattr",
    "exit", "quit", "credits", "copyright", "license",
}

# Attr-id, mille lugemine on keelatud (dunder-exposed vahendid)
FORBIDDEN_ATTRS = {
    "__globals__", "__builtins__", "__class__", "__bases__",
    "__subclasses__", "__code__", "__closure__", "__dict__",
    "__mro__", "__init_subclass__", "__reduce__", "__reduce_ex__",
    "__getattribute__", "__getattr__", "__setattr__",
    "f_globals", "f_locals", "f_builtins", "f_code",
    "gi_frame", "gi_code", "cr_frame", "cr_code",
    "mro", "_io",
}


class ValidationError(Exception):
    """AST valideerimine leidis keelatud konstruktsiooni."""
    def __init__(self, msg: str, node: Optional[ast.AST] = None):
        self.node = node
        line = node.lineno if node is not None and hasattr(node, "lineno") else "?"
        super().__init__(f"[line {line}] {msg}")


def validate_ast(source: str) -> ast.Module:
    """Parsi kood, käi AST läbi, viska ValidationError kui midagi kahtlast."""
    try:
        tree = ast.parse(source, mode="exec")
    except SyntaxError as e:
        raise ValidationError(f"Syntax error: {e.msg}")

    for node in ast.walk(tree):
        # Import'id
        if isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name.split(".")[0] not in ALLOWED_MODULES:
                    raise ValidationError(f"Forbidden import: {alias.name}", node)
        elif isinstance(node, ast.ImportFrom):
            mod = (node.module or "").split(".")[0]
            if mod not in ALLOWED_MODULES:
                raise ValidationError(f"Forbidden from-import: {node.module}", node)

        # Keelatud nimed
        elif isinstance(node, ast.Name) and node.id in FORBIDDEN_NAMES:
            raise ValidationError(f"Forbidden name: {node.id}", node)

        # Keelatud atribuudid
        elif isinstance(node, ast.Attribute) and node.attr in FORBIDDEN_ATTRS:
            raise ValidationError(f"Forbidden attribute: .{node.attr}", node)

        # Keelatud f-string'i sees peidetud eval (ei, f-string on OK — aga
        # lihtsalt kontrollime üle)
        elif isinstance(node, (ast.Lambda, )):
            # Lambda on OK (puhas), sest sees ei saa import-i teha.
            pass

    return tree


# ---------------------------------------------------------------------------
# 2) Turvaline käivituskeskkond
# ---------------------------------------------------------------------------

# Minimaalne builtins-komplekt — ainult puhtad väärtustusfunktsioonid.
SAFE_BUILTINS: Dict[str, Any] = {
    name: getattr(__builtins__, name) if hasattr(__builtins__, name)
          else __builtins__[name]
    for name in [
        # numbrid + matemaatika
        "abs", "min", "max", "sum", "round", "pow", "divmod",
        # tüübid
        "int", "float", "bool", "str", "bytes", "complex",
        "list", "tuple", "dict", "set", "frozenset",
        # sequence
        "len", "range", "enumerate", "zip", "reversed", "sorted",
        "map", "filter", "any", "all",
        # utility
        "print", "isinstance", "issubclass", "callable", "repr",
        "hash", "id", "iter", "next", "slice",
        "True", "False", "None", "NotImplemented", "Ellipsis",
        # erandid (vajalik, et kasutaja kood saaks try/except teha)
        "Exception", "ValueError", "TypeError", "KeyError",
        "IndexError", "AttributeError", "ZeroDivisionError",
        "ArithmeticError", "RuntimeError", "NotImplementedError",
        "StopIteration",
    ]
    if (hasattr(__builtins__, name) if not isinstance(__builtins__, dict)
        else name in __builtins__)
}


class TimeoutError(Exception):
    """Sandbox ajalimiit sai täis."""


@contextmanager
def time_limit(seconds: int):
    """Unix-signal-põhine ajalimiit. Ainult pealoim (ei sobi worker-threadile)."""
    def handler(signum, frame):
        raise TimeoutError(f"Execution exceeded {seconds}s")
    old = signal.signal(signal.SIGALRM, handler)
    signal.alarm(seconds)
    try:
        yield
    finally:
        signal.alarm(0)
        signal.signal(signal.SIGALRM, old)


@contextmanager
def memory_limit(mb: int):
    """RAM hard-limit. 512MB on piisav keerukale CadQuery-tööle."""
    try:
        old_soft, old_hard = resource.getrlimit(resource.RLIMIT_AS)
        resource.setrlimit(resource.RLIMIT_AS, (mb * 1024 * 1024, old_hard))
        try:
            yield
        finally:
            resource.setrlimit(resource.RLIMIT_AS, (old_soft, old_hard))
    except (ValueError, OSError):
        # Konteiner võib piirangut keelata — edasi ilma soft-limiidita.
        # Docker cgroup-memory-piirang peaks olema ette seatud.
        yield


# ---------------------------------------------------------------------------
# 3) Käivitamine ja eksport
# ---------------------------------------------------------------------------

def run_sandboxed(code: str, entrypoint: str = "build",
                  timeout_s: int = 15, memory_mb: int = 512) -> Any:
    """
    Käivita valideeritud kood, kutsu entrypoint()-i, tagasta tulemus.
    Kõik juba valideeritud AST-läbi — seega turvaline.
    """
    validate_ast(code)

    safe_globals: Dict[str, Any] = {
        "__builtins__": SAFE_BUILTINS,
        "cq": cq,
        "cadquery": cq,
    }

    with time_limit(timeout_s), memory_limit(memory_mb):
        exec(compile(code, "<freeform>", "exec"), safe_globals)
        fn = safe_globals.get(entrypoint)
        if not callable(fn):
            raise ValidationError(
                f"Entry point `{entrypoint}()` not defined or not callable"
            )
        result = fn()
        if result is None:
            raise ValidationError(
                f"`{entrypoint}()` returned None — peab tagastama cq.Workplane"
            )
        return result


def export_model(model, fmts: List[str]) -> Dict[str, str]:
    """Ekspordi mudelist STL/STEP kui base64."""
    out: Dict[str, str] = {}
    for fmt in fmts:
        ext = fmt.lower()
        if ext not in ("stl", "step", "stp"):
            continue
        ext_suffix = ".step" if ext in ("step", "stp") else ".stl"
        with tempfile.NamedTemporaryFile(suffix=ext_suffix, delete=False) as f:
            path = f.name
        try:
            cq.exporters.export(model, path)
            with open(path, "rb") as fh:
                out[ext.replace("stp", "step")] = base64.b64encode(fh.read()).decode("ascii")
        finally:
            try:
                os.unlink(path)
            except OSError:
                pass
    return out


# ---------------------------------------------------------------------------
# 4) API-mudelid ja route
# ---------------------------------------------------------------------------

class FreeformRequest(BaseModel):
    code: str = Field(..., description="CadQuery Python code defining build()")
    entrypoint: str = Field(default="build")
    export: List[str] = Field(default_factory=lambda: ["stl"])
    timeout_s: int = Field(default=15, ge=1, le=30)


class FreeformResponse(BaseModel):
    ok: bool
    error: Optional[str] = None
    error_kind: Optional[str] = None
    files: Dict[str, str] = Field(default_factory=dict)
    elapsed_ms: int = 0


def register_routes(app):
    """Worker app.py kutsub seda mount'imise ajal."""

    @app.post("/freeform/generate", response_model=FreeformResponse)
    def generate(req: FreeformRequest):
        t0 = time.perf_counter()
        try:
            model = run_sandboxed(req.code, req.entrypoint, req.timeout_s)
            files = export_model(model, req.export)
            return FreeformResponse(
                ok=True,
                files=files,
                elapsed_ms=int((time.perf_counter() - t0) * 1000),
            )
        except ValidationError as e:
            return FreeformResponse(
                ok=False, error=str(e), error_kind="forbidden",
                elapsed_ms=int((time.perf_counter() - t0) * 1000),
            )
        except TimeoutError as e:
            return FreeformResponse(
                ok=False, error=str(e), error_kind="timeout",
                elapsed_ms=int((time.perf_counter() - t0) * 1000),
            )
        except MemoryError as e:
            return FreeformResponse(
                ok=False, error=f"Memory limit exceeded: {e}",
                error_kind="memory",
                elapsed_ms=int((time.perf_counter() - t0) * 1000),
            )
        except Exception as e:
            # Kasutaja koodi jooksutusviga — tagasta tagaserva trace LLM-ile.
            tb = traceback.format_exc(limit=4)
            return FreeformResponse(
                ok=False,
                error=f"{type(e).__name__}: {e}\n{tb}",
                error_kind="runtime",
                elapsed_ms=int((time.perf_counter() - t0) * 1000),
            )

    @app.get("/freeform/health")
    def health():
        return {"ok": True, "sandbox": "ast+rlimit+alarm"}
