"""
Observability bootstrap workerile.

Kasutus app.py's:
    from fastapi import FastAPI
    from observability import setup_observability

    app = FastAPI(title="ai-cad-worker")
    setup_observability(app, service_name="ai-cad-worker")

Lisab:
    - /metrics endpoint (Prometheus scrape format)
    - OTLP tracing exporter (kui OTEL_EXPORTER_OTLP_ENDPOINT on seatud)
    - FastAPI + httpx auto-instrumentation (span'id trapitakse automaatselt)
    - Custom CadQuery template-specific metrics:
        * cadquery_generate_duration_seconds (histogram per template_id)
        * cadquery_generate_total (counter per template_id, status)
        * cadquery_stl_bytes (histogram per template_id)
"""
from __future__ import annotations

import os
import time
from contextlib import contextmanager

from fastapi import FastAPI
from prometheus_client import Counter, Histogram
from prometheus_fastapi_instrumentator import Instrumentator


# ── Custom meetrikad (worker-spetsiifilised) ─────────────────────────
cadquery_generate_duration = Histogram(
    "cadquery_generate_duration_seconds",
    "CadQuery generate() kestus (sekundites), per template",
    labelnames=("template_id",),
    buckets=(0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30),
)

cadquery_generate_total = Counter(
    "cadquery_generate_total",
    "CadQuery generate() kutsete arv, per template ja staatus",
    labelnames=("template_id", "status"),
)

cadquery_stl_bytes = Histogram(
    "cadquery_stl_bytes",
    "Genereeritud STL failide suurus baitides, per template",
    labelnames=("template_id",),
    buckets=(1_000, 10_000, 50_000, 100_000, 500_000, 1_000_000, 5_000_000, 20_000_000),
)


@contextmanager
def track_generate(template_id: str):
    """Mugav context manager generate() ümber.

    Kasutus:
        with track_generate("hook") as rec:
            stl_bytes = generate_hook(params)
            rec.stl_size(len(stl_bytes))
    """
    start = time.perf_counter()
    status = "success"

    class _Rec:
        def stl_size(self, n: int):
            cadquery_stl_bytes.labels(template_id=template_id).observe(n)

    rec = _Rec()
    try:
        yield rec
    except Exception:
        status = "failure"
        raise
    finally:
        elapsed = time.perf_counter() - start
        cadquery_generate_duration.labels(template_id=template_id).observe(elapsed)
        cadquery_generate_total.labels(
            template_id=template_id, status=status
        ).inc()


def setup_observability(app: FastAPI, service_name: str) -> None:
    """Aktiveeri Prometheus + OTel FastAPI rakendusel."""
    # Prometheus — /metrics endpoint
    Instrumentator(
        should_group_status_codes=True,
        should_ignore_untemplated=True,
        should_respect_env_var=True,
        env_var_name="ENABLE_METRICS",
        excluded_handlers=["/metrics", "/health"],
    ).instrument(app).expose(app, endpoint="/metrics", include_in_schema=False)

    # OTLP tracing — ainult kui endpoint on seatud
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
        from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
    except ImportError:
        return  # observability deps pole paigaldatud, OK

    resource = Resource.create({SERVICE_NAME: service_name})
    provider = TracerProvider(resource=resource)
    exporter = OTLPSpanExporter(endpoint=f"{otlp_endpoint}/v1/traces")
    provider.add_span_processor(BatchSpanProcessor(exporter))
    trace.set_tracer_provider(provider)

    FastAPIInstrumentor.instrument_app(app, excluded_urls="/metrics,/health")
    HTTPXClientInstrumentor().instrument()
