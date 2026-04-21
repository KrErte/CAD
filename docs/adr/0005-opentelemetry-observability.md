# ADR-0005: OpenTelemetry + Grafana stack observability'iks

- **Status**: accepted
- **Kuupäev**: 2026-04-18
- **Otsustajad**: @olen-krerte
- **Tehniline keerukus**: M

## Kontekst

Kolm-komponentne request-path (backend → worker → slicer) muutis
debugging'u keeruliseks: kus see 30s viivitus tuli? Kas Claude, worker
või slicer? Ilma distributed tracing'uta oli ainus tee printf + grep.

Lisaks: Claude-kulu meetriku ei olnud — Stripe-arve ilmumine oli ainus
signaal, et ootamatult hakkame raha põletama.

## Variandid

### Variant A: Prints + grep logs
- Plussid: ei maksa
- Miinused: ei tööta prod'is, ei korrelee requestid, mäluleak'i ei näe

### Variant B: Datadog / New Relic SaaS
- Plussid: kõik karbis, nice UI
- Miinused: kulukus (~€200+/kuu 10 GB log'i jaoks), vendor lock-in

### Variant C: OpenTelemetry → Grafana Cloud
- Plussid: vendor-neutral otad (OTel), Grafana free tier on lahke
- Miinused: initial setup ~3 päeva

### Variant D: Self-hosted Grafana + Prometheus + Loki + Tempo
- Plussid: täielik kontroll, ei maksa licence'i
- Miinused: eraldi opsida

## Otsus

**OpenTelemetry instrumentation** + initial **self-hosted** Grafana stack
(Variant D). OTel tagab, et ad-hoc saame migreerida mistahes backend'i
(Datadog OTel ingest, Grafana Cloud jne).

Components:
- Prometheus — metrics (Micrometer scrape)
- Loki — struktureeritud logid (Loki4j appender)
- Tempo — traces (OTLP/HTTP)
- Grafana — UI + dashboards + alerts

## Tagajärjed

### Positiivsed
- Request-ID + TraceID korrelee Loki logide ja Tempo trace'idega
- Claude €/24h stat-paneel näitab burn-rate'i live'is
- Alerting (p99 latency, error rate, Claude budget burn) annab 15 min
  varase hoiatuse enne incident'i
- SLO violation'id tulevad Prometheus alertmanager'ist Slack'i

### Negatiivsed
- ~500MB lisamäluga backend (Micrometer + OTel SDK)
- Tempo storage kasvab (trace retention 7 päeva, ~2GB/nädal)
- Ops: Grafana peab olema kõrgelt available (dashboard on deploy-kriitiline)

### Risk'id
- **Risk**: trace-id lekkib logi, aga sample-rate 0.1 => 90% request'idest
  pole trace'i. **Maandamine**: head-based sampling + tail-based samplerid
  tähtsatele (error'id alati sample'itakse)
- **Risk**: self-host stack ei skaleerumise peal. **Maandamine**: selge
  migreerimis-plaan Grafana Cloud'i, kui Loki > 100GB/kuu

## Viited
- `docs/OBSERVABILITY.md`
- `observability/prometheus/rules/ai-cad.yml`
