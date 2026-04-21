# Observability

AI-CAD kasutab **three pillars of observability** mustrit — metrics, logs, traces.
Kõik kolm on integreeritud niimoodi, et Grafanas saad logist klikkida trace'i ja
trace'ist logisse samal traceId-l.

## Stack

| Pillar  | Tööriist          | Kust tuleb                                           |
| ------- | ----------------- | ---------------------------------------------------- |
| Metrics | Prometheus        | Backend: `/actuator/prometheus` (Micrometer)          |
|         |                   | Worker / Slicer: `/metrics` (prometheus-fastapi)      |
| Logs    | Grafana Loki      | Spring Boot → Loki4j appender (kui `LOKI_URL` seatud) |
|         |                   | Python: stdout → promtail (prod)                      |
| Traces  | Grafana Tempo     | OTLP/HTTP (:4318) — Micrometer Tracing + OTel SDK     |

## Lokaalne arendus

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

Siis:

- **Grafana**: <http://localhost:3000> (admin / admin)
- **Prometheus**: <http://localhost:9090>
- **Loki**: <http://localhost:3100>
- **Tempo**: <http://localhost:3200>

Dashboardid on eelprovisjoneeritud kausta `AI-CAD` alla — kohe pärast
käivitamist avaneb **AI-CAD Overview** koos reaalajas numbritega.

## Backend — custom meetrikad

Kõige huvitavamad meetrikad Claude-spetsiifilises domeenis:

```promql
# Kui palju raha oleme Claude peale kulutanud tänasel kuupäeval?
sum(increase(claude_api_cost_eur_total[24h]))

# Input vs output token rate (kas Claude vastab pikalt?)
sum(rate(claude_api_tokens_output_total[5m]))
/
sum(rate(claude_api_tokens_input_total[5m]))

# Claude API p95 latency mudeli kohta
histogram_quantile(0.95,
  sum(rate(claude_api_duration_seconds_bucket[5m])) by (le, model)
)

# External services latency võrdlus (worker vs slicer vs meshy)
histogram_quantile(0.95,
  sum(rate(external_api_duration_seconds_bucket[5m])) by (le, service)
)
```

### Kuidas kasutada koodis

Claude API kutsumisel `ClaudeClient.java`:

```java
long start = System.nanoTime();
try {
    var response = anthropic.messages().create(request);
    claudeCostMetrics.recordUsage(
        model,
        response.usage().inputTokens(),
        response.usage().outputTokens(),
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    );
    return response;
} catch (RateLimitException e) {
    claudeCostMetrics.recordFailure(model, "rate_limit",
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    throw e;
}
```

Worker `@Timed`-ed endpoint'id:

```python
from observability import track_generate

@app.post("/generate")
def generate(req: GenRequest):
    with track_generate(req.template) as rec:
        stl_bytes = TEMPLATES[req.template](req.params)
        rec.stl_size(len(stl_bytes))
        return Response(stl_bytes, media_type="application/sla")
```

## Health checks

Kolm eraldi endpoint'i:

| Endpoint                       | Kasutus                                    |
| ------------------------------ | ------------------------------------------ |
| `/actuator/health/liveness`    | Pod restart — ainult livenessState         |
| `/actuator/health/readiness`   | Traffic routing — DB + worker + slicer + Claude API võti |
| `/actuator/health`             | Koondnäit — võib nõuda autentimist         |

K8s deployment'is on kolm probe'i:

- **startupProbe** — annab Spring Boot'ile kuni 150s (30 × 5s) käivitusajaks
- **livenessProbe** — iga 10s kontrolli, kas JVM vastab
- **readinessProbe** — iga 5s kontrolli, kas **kõik** dependent service'id on UP

Kui worker kukub, siis backend readiness läheb DOWN ja k8s tõmbab pod'i
Service'ist välja, kuni worker tagasi. Ei kuku 500-eid kasutajale.

## Traces

OpenTelemetry Micrometer Observation Bridge loob automaatselt span'i iga HTTP
päringu ja iga välise kutsu ümber. Backend propageerib W3C `traceparent`
header'i WebClient'i kaudu worker'i ja slicer'ile, kus FastAPI OTel
instrumentation selle korjab. Tulemuseks näed Grafana Tempo's ühe trace'i,
mis läbib `backend → worker → slicer`.

Samad traceId'd on MDC-s logides, nii et Loki ↔ Tempo mapping toimib.

### Sample rate

- **Dev**: `TRACING_SAMPLE=1.0` (100%, vaatleme kõike)
- **Prod**: `TRACING_SAMPLE=0.1` (10%, maksab vähem)

Error span'id (5xx, exceptions) saaks tail-sampler'iga 100%-liselt salvestada,
kui kasutame OTel Collector'it vahepeal (roadmap).

## Alerting

Prometheus alert rules on `observability/prometheus/rules/ai-cad.yml`:

| Alert                    | Trigger                                          |
| ------------------------ | ------------------------------------------------ |
| `HttpLatencyP99High`     | p99 > 2s üle 10 min                              |
| `ClaudeCostBurnRateHigh` | > 10 €/h Claude API kuludest                     |
| `BackendDown`            | Prometheus scrape fail > 2 min                   |
| `WorkerDown`             | sama, worker sidecar                             |
| `ErrorRateHigh`          | 5xx rate > 5% üle 5 min                          |
| `HikariPoolSaturated`    | DB connection pool > 90% üle 5 min               |

Alertmanager pole veel konfigureeritud — roadmap: Slack + email notification.

## Prod deploy

### Eeldus

Klastris on paigaldatud `kube-prometheus-stack`:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace \
  --set grafana.sidecar.dashboards.enabled=true \
  --set grafana.sidecar.dashboards.searchNamespace=ALL
```

### Enable AI-CAD observability

Meie chart loob automaatselt:

- `ServiceMonitor` backend'ile → Prometheus skräpib /actuator/prometheus
- `ConfigMap` AI-CAD dashboardiga → Grafana sidecar tõmbab üles

```bash
helm upgrade ai-cad ./helm/ai-cad \
  --set observability.tracing.enabled=true \
  --set observability.tracing.otlpEndpoint=http://tempo.monitoring:4318 \
  --set observability.logging.lokiUrl=http://loki.monitoring:3100 \
  --set observability.serviceMonitor.labels.release=monitoring
```

## Roadmap

- [ ] OTel Collector + tail-sampling (100% error traces)
- [ ] Alertmanager → Slack / email
- [ ] SLO dashboard (availability, error budget burn rate)
- [ ] Frontend RUM (Real User Monitoring) — Sentry Performance
- [ ] Synthetic monitoring (k6 cron, uptime-kuma, vmi.kõik.ee-style)
