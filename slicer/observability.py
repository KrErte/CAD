"""
Observability bootstrap slicer sidecarile.

Kasutus app.py's:
    from fastapi import FastAPI
    from observability import setup_observability

    app = FastAPI(title="ai-cad-slicer")
    setup_observability(app, service_name="ai-cad-slicer")

Lisab:
    - /metrics endpoint (Prometheus scrape format)
    - OTLP tracing exporter (kui OTEL_EXPORTER_OTLP_ENDPOINT seatud)
    - FastAPI auto-instrumentation
    - Custom slice-specific metrics:
        * slicer_slice_duration_seconds (histogram per profile)
        * slicer_slice_total (counter per profile + status)
        * slicer_print_time_hours (histogram per profile — arvutatud prindiaeg)
        * slicer_filament_grams (histogram per profile — filamendi mass)
"""
from __future__ import annotations

import os
import time
from contextlib import contextmanager

from fastapi import FastAPI
from prometheus_client import Counter, Histogram
from prometheus_fastapi_instrumentator import Instrumentator


slice_duration = Histogram(
    "slicer_slice_duration_seconds",
    "PrusaSlicer CLI kutse kestus",
    labelnames=("profile",),
    buckets=(1, 2.5, 5, 10, 30, 60, 120, 300, 600),
)

slice_total = Counter(
    "slicer_slice_total",
    "Slice kutsete arv, per profile + staatus",
    labelnames=("profile", "status"),
)

print_time_hours = Histogram(
    "slicer_print_time_hours",
    "Arvutatud prindiaeg tundides",
    labelnames=("profile",),
    buckets=(0.25, 0.5, 1, 2, 4, 8, 16, 32, 72),
)

filament_grams = Histogram(
    "slicer_filament_grams",
    "Filamendi mass grammides",
    labelnames=("profile",),
    buckets=(5, 10, 25, 50, 100, 250, 500, 1000, 2000),
)


@contextmanager
def track_slice(profile: str):
    start = time.perf_counter()
    status = "success"

    class _Rec:
        def print_time(self, hours: float):
            if hours > 0:
                print_time_hours.labels(profile=profile).observe(hours)

        def filament_mass(self, grams: float):
            if grams > 0:
                filament_grams.labels(profile=profile).observe(grams)

    rec = _Rec()
    try:
        yield rec
    except Exception:
        status = "failure"
        raise
    finally:
        slice_duration.labels(profile=profile).observe(time.perf_counter() - start)
        slice_total.labels(profile=profile, status=status).inc()


def setup_observability(app: FastAPI, service_name: str) -> None:
    Instrumentator(
        should_group_status_codes=True,
        should_ignore_untemplated=True,
        excluded_handlers=["/metrics", "/health"],
    ).instrument(app).expose(app, endpoint="/metrics", include_in_schema=False)

    otlp_endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
    if not otlp_endpoint:
        return

    try:
        from opentelemetry import trace
        from opentelemetry.sdk.resources import Resource, SERVICE_NAME
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
        from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    except ImportError:
        return

    resource = Resource.create({SERVICE_NAME: service_name})
    provider = TracerProvider(resource=resource)
    provider.add_span_processor(
        BatchSpanProcessor(OTLPSpanExporter(endpoint=f"{otlp_endpoint}/v1/traces"))
    )
    trace.set_tracer_provider(provider)

    FastAPIInstrumentor.instrument_app(app, excluded_urls="/metrics,/health")
