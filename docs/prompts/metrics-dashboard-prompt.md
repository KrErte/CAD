# Prompt Claude Code'ile — metrics-dashboard Prometheus + Grafana'ga

Kopeeri kogu alljärgnev osa "---" joonte vahelt Claude Code'i.

---

# Ülesanne: seadista Prometheus + Grafana dashboard äri- ja tehnilisteks meetrikuteks

## Konteksts

Meie süsteem toodab juba palju meetrikuid Micrometer'i kaudu (`/actuator/prometheus` endpoint), aga keegi neid ei vaata. Ilma dashboard'ita on otsustamine ("kas tõsta Creator-plaani hinda?", "kas Meshy-fallback langeb pärast Forge-laienemist?") puhas gut-feel.

See PR seadistab lokaalselt ja k8s-is Prometheus'i + Grafana, provisioneerib ühe peamise dashboardi **AI-CAD Operations** neljas sektsioonis: äri, ühiku-ökonoomika, toote-tervis, Forge-pipeline. Lisaks lisame backend'isse äri-meetrikute eksportija, mis Postgres'ist loeb äri-andmeid ja publikeerib Micrometer-gauge'idena.

## Mida teha

### 1. Docker Compose (lokaalne arendus)

Leia `docker-compose.yml` ja lisa kaks uut teenust:

```yaml
prometheus:
  image: prom/prometheus:v2.54.1
  ports:
    - "9090:9090"
  volumes:
    - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    - prometheus_data:/prometheus
  command:
    - '--config.file=/etc/prometheus/prometheus.yml'
    - '--storage.tsdb.retention.time=30d'
  depends_on:
    - backend

grafana:
  image: grafana/grafana:11.2.0
  ports:
    - "3001:3000"   # 3000 on juba frontend'il
  environment:
    GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}
    GF_USERS_ALLOW_SIGN_UP: "false"
  volumes:
    - grafana_data:/var/lib/grafana
    - ./grafana/provisioning:/etc/grafana/provisioning:ro
    - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
  depends_on:
    - prometheus

volumes:
  prometheus_data:
  grafana_data:
```

Lisa `GRAFANA_ADMIN_PASSWORD` ka `.env.example`-isse.

### 2. Prometheus konfiguratsioon

Loo `prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'ai-cad-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['backend:8080']

  - job_name: 'ai-cad-worker'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['worker:8000']
```

Kui worker'il pole veel `/metrics` endpoint'i, lisa see FastAPI-sse `prometheus_fastapi_instrumentator` abil (Python package, 5-realine seadistus).

### 3. Grafana provisioning

**Datasource-provisioning**: `grafana/provisioning/datasources/datasources.yml`

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true

  - name: Postgres
    type: postgres
    url: postgres:5432
    database: aicad
    user: aicad_readonly
    secureJsonData:
      password: ${POSTGRES_READONLY_PASSWORD}
    jsonData:
      sslmode: disable
      postgresVersion: 1500
      timescaledb: false
```

Loo eraldi `aicad_readonly` DB-kasutaja read-only õigustega (lisa uus Flyway-migration, mis `CREATE ROLE aicad_readonly WITH LOGIN PASSWORD ...; GRANT SELECT ON ALL TABLES IN SCHEMA public TO aicad_readonly;`). Password võta `POSTGRES_READONLY_PASSWORD` env'ist.

**Dashboard-provisioning**: `grafana/provisioning/dashboards/dashboard-provider.yml`

```yaml
apiVersion: 1
providers:
  - name: 'ai-cad'
    folder: 'AI-CAD'
    type: file
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
```

**Peamine dashboard-fail**: `grafana/dashboards/ai-cad-operations.json`

See on suur JSON. Loo see Grafana'st UI-s peaaegu-valmis, siis export JSON-ina (parim viis), või kirjuta käsitsi kui oskad. Dashboardi peavad olema järgmised **16 paneeli, jaotatud 4 ritta** (sektsiooni-pealkirjadega):

#### Rida 1 — **Business Health** (Postgres-datasource)

1. **Monthly Recurring Revenue (MRR)** — single-stat + sparkline. Query:
   ```sql
   SELECT SUM(
     CASE plan
       WHEN 'MAKER' THEN 9
       WHEN 'CREATOR' THEN 29
       WHEN 'BUREAU_STARTER' THEN 49
       WHEN 'BUREAU_STUDIO' THEN 199
       WHEN 'BUREAU_FACTORY' THEN 499
       WHEN 'API_GROWTH' THEN 49
       WHEN 'API_BUSINESS' THEN 199
       ELSE 0
     END
   ) AS mrr
   FROM subscriptions WHERE status = 'active'
   ```
2. **Active Subscriptions by Plan** — pie chart või horizontal bar
3. **New Signups (24h / 7d / 30d)** — stat-grupp
4. **Trial → Paid Conversion Rate** — gauge, roheline kui >15%

#### Rida 2 — **Unit Economics** (Prometheus)

5. **Token Spend by Model** (today / this month) — stacked bar chart, Haiku / Sonnet / Meshy eraldi
6. **Cost per Generation** — line chart ajas, arvutatakse: `rate(token_cost_total) / rate(generations_total)`. Eesmärk: peab langema ajas kui cache + Forge töötavad.
7. **Cache Hit Rate** — gauge, 0–100%. Query: `rate(intent_cache_hit_total[5m]) / (rate(intent_cache_hit_total[5m]) + rate(intent_cache_miss_total[5m]))`
8. **Estimated Monthly Savings from Cache** — stat: `rate(intent_cache_hit_total[30d]) * 0.002 * 60 * 60 * 24 * 30`

#### Rida 3 — **Product Health** (Prometheus)

9. **API Latency (p50 / p95 / p99)** — line chart, endpoint'ide kaupa
10. **Error Rate (5xx)** — gauge, punane kui >1%
11. **Top 10 Templates by Usage (7d)** — horizontal bar chart. Vajab et worker emit'iks counter'it `template_usage_total{template="shelf_bracket"}`.
12. **Active Users (24h / 7d / 30d)** — stat-grupp. Query Postgres'ist `SELECT COUNT(DISTINCT user_id) FROM api_logs WHERE created_at > NOW() - INTERVAL '24 hours'`

#### Rida 4 — **Template Forge Pipeline** (Postgres + Prometheus)

13. **Candidates Pipeline** — funnel chart: pending → generated → approved
14. **Meshy Fallback Rate (7d)** — line chart. Query: `rate(meshy_fallback_total[1d]) / rate(generations_total[1d])`. Eesmärk: langeb ajas.
15. **Estimated Meshy Savings from Approved Templates** — stat, SQL:
    ```sql
    SELECT SUM(occurrence_count) * 0.25 AS savings_eur
    FROM template_candidates WHERE status = 'approved'
    ```
16. **Auto-Generated Template Cost** — stat: `sum(forge_generation_cost_eur_total)`. Näitab kui palju oleme Claude Sonnet'ile kulutanud koodi-genereerimisele.

Dashboardi JSON struktuur: kasuta `schemaVersion: 39`, `refresh: "30s"`, `time: {"from": "now-24h", "to": "now"}`. Loo template-variable `$period` (1h/6h/24h/7d/30d), mida paneelid saavad kasutada.

### 4. Backend: `BusinessMetricsExporter`

Failitee: `backend/src/main/java/com/aicad/metrics/BusinessMetricsExporter.java`

Spring component mis `@Scheduled(fixedRate = 60_000)` (iga minut) query'b Postgres'ist ja publikeerib Micrometer-gauge'idena:

```java
@Component
public class BusinessMetricsExporter {
    private final MeterRegistry registry;
    private final JdbcTemplate jdbc;
    private final AtomicLong mrr = new AtomicLong(0);
    private final AtomicLong activeSubscriptions = new AtomicLong(0);
    // ... teised gauge-state'id

    @PostConstruct
    void registerGauges() {
        Gauge.builder("business.mrr.eur", mrr, AtomicLong::get)
             .description("Current MRR in EUR")
             .register(registry);
        // ... teised
    }

    @Scheduled(fixedRate = 60_000)
    void refresh() {
        mrr.set(queryMrr());
        activeSubscriptions.set(queryActiveSubscriptions());
        // ...
    }
}
```

Ekspordi meetrikud: `business.mrr.eur`, `business.subscriptions.active`, `business.subscriptions.by_plan` (tag'iga `plan=X`), `business.signups.last_24h`, `business.trials.conversion_rate`, `business.users.active_30d`.

### 5. Token-cost tracking (vajalik unit-economics paneelide jaoks)

Uus teenus: `backend/src/main/java/com/aicad/metrics/TokenCostTracker.java`

```java
@Component
public class TokenCostTracker {
    private final Counter haikuCost;
    private final Counter sonnetCost;
    private final Counter meshyCost;

    public TokenCostTracker(MeterRegistry registry) {
        haikuCost = Counter.builder("token.cost.eur")
            .tag("model", "claude-haiku")
            .register(registry);
        sonnetCost = Counter.builder("token.cost.eur")
            .tag("model", "claude-sonnet")
            .register(registry);
        meshyCost = Counter.builder("token.cost.eur")
            .tag("model", "meshy")
            .register(registry);
    }

    public void recordHaiku(int inputTokens, int outputTokens) {
        double cost = (inputTokens * 1.0 / 1_000_000) + (outputTokens * 5.0 / 1_000_000);
        haikuCost.increment(cost);
    }

    public void recordSonnet(int inputTokens, int outputTokens) {
        double cost = (inputTokens * 3.0 / 1_000_000) + (outputTokens * 15.0 / 1_000_000);
        sonnetCost.increment(cost);
    }

    public void recordMeshy() {
        meshyCost.increment(0.25);  // flat per-call, kohenda kui Meshy muudab hinda
    }
}
```

Integreeri:
- `ClaudeClient.parseIntent()` — pärast eduka kõne kutsu `tokenCostTracker.recordHaiku(usage.input, usage.output)`
- `ClaudeClient.reviewDesign()` — `recordSonnet(...)`
- `MeshyClient.textToMesh()` — `recordMeshy()`
- `TemplateCodeGenerator` — `recordSonnet(...)`

Anthropic API vastus sisaldab `usage.input_tokens` ja `usage.output_tokens` — kasuta neid.

### 6. K8s / Helm uuendus

**Helm chart**: `helm/ai-cad/templates/` lisa:

- `prometheus-deployment.yaml` + `prometheus-service.yaml` + `prometheus-pvc.yaml`
- `prometheus-configmap.yaml` sisaldab `prometheus.yml`-i sisu
- `grafana-deployment.yaml` + `grafana-service.yaml` + `grafana-pvc.yaml`
- `grafana-configmap.yaml` sisaldab provisioning-YAML-e + dashboard-JSON-i

Kui see muudab Helm chart'i liiga suureks, tee eraldi alamchart `helm/ai-cad/charts/monitoring/` ja include'i `values.yaml`-ist `monitoring.enabled: true` flagi taga.

**Grafana ingress** (production): `grafana.minu-domeen.ee` koos basic-auth'iga (ainult admin'idele). Hariduseks: Grafana'l endas on login, aga avaliku URL-i eest kaitseks pane cloudflare-tüüpi access ette või nginx basic-auth.

**K8s secret'id**:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: grafana-credentials
type: Opaque
stringData:
  admin-password: "..."
  postgres-readonly-password: "..."
```

### 7. Alert'id (minimaalsed)

Loo `prometheus/alerts.yml` + mounte Prometheus'isse (uuenda `prometheus.yml` `rule_files`-i):

```yaml
groups:
  - name: ai-cad-critical
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
        for: 5m
        annotations:
          summary: "5xx error rate > 5% for 5 minutes"

      - alert: TokenSpendSpike
        expr: rate(token_cost_eur_total[1h]) > (avg_over_time(rate(token_cost_eur_total[1h])[24h:1h]) * 3)
        for: 15m
        annotations:
          summary: "Token spend 3x higher than 24h baseline — possible abuse or bug"

      - alert: CacheHitRateLow
        expr: (rate(intent_cache_hit_total[1h]) / (rate(intent_cache_hit_total[1h]) + rate(intent_cache_miss_total[1h]))) < 0.1
        for: 1h
        annotations:
          summary: "Intent cache hit rate < 10% — normalizer may be broken"

      - alert: RedisDown
        expr: up{job="redis"} == 0
        for: 2m
        annotations:
          summary: "Redis is down — caches fallback to in-memory (cost risk)"
```

Alertmanager'i seadistamist **jäta välja** — ainult loo alert-rules. Email/Slack-integratsioon on eraldi iteratsioon.

### 8. Testid

- Backend: `BusinessMetricsExporterTest.java` — testcontainers Postgres'iga, seeda näidis-andmed, käivita refresh(), kontrolli et gauge-väärtused on õiged
- Backend: `TokenCostTrackerTest.java` — kutsu recordHaiku/Sonnet/Meshy, kontrolli et counter inkrementeeub õige summaga
- Docker-compose "smoke test" shell-skript `scripts/test-metrics-stack.sh`:
  - `docker compose up -d prometheus grafana`
  - `curl http://localhost:9090/-/ready` — Prometheus tervis
  - `curl http://localhost:3001/api/health` — Grafana tervis
  - `curl "http://localhost:9090/api/v1/targets"` — peavad näitama `ai-cad-backend` scraped

### 9. Dokumentatsioon

Uus fail `docs/METRICS.md`:

- Mis on dashboardis, kuidas iga paneeli lugeda
- Kuidas lokaalselt käivitada (`docker compose up grafana prometheus`, URL http://localhost:3001, login admin/admin)
- Kuidas uusi paneeleid lisada (Grafana UI → salvesta → export JSON → commit)
- Kuidas k8s-is juurde pääseda (port-forward või ingress)
- Tähtsaimad KPI-d mida vaadata nädalas/kuus:
  - MRR trend
  - Trial conversion rate
  - Meshy fallback rate (eesmärk langeda)
  - Cache hit rate (eesmärk kasvada)
  - Top 3 templates (iga kvartal'i lõpus uurida kas laienda)

## Piirangud

- **Ära integreeri** Sentry't, Datadog'i või muud kolmanda osapoole observability-tööriista — Prometheus + Grafana on self-hosted ja piisav
- **Ära seadista** Alertmanager'it Slack/email-integreerinuks — alert-rules on olemas, teavitused tulevad eraldi PR-iga
- **Ära lisa** distributed tracing'ut (Jaeger, Tempo) — see on v2
- **Ära muuda** olemasolevaid meetrikute nimesid kui on — lisa ainult uusi

## Acceptance criteria

1. `docker compose up` käivitab Prometheus + Grafana konfliktideta
2. `http://localhost:3001` — login admin'iga — näitab AI-CAD folder'i üht dashboardi "AI-CAD Operations"
3. Kõik 16 paneeli renderdavad ilma "No Data" veata (seeda dev-DB ja tee mõned API-kõned esmalt)
4. `curl http://localhost:8080/actuator/prometheus | grep business_mrr_eur` tagastab kehtiva väärtuse
5. `curl http://localhost:8080/actuator/prometheus | grep token_cost_eur_total` näitab 3 rida (Haiku, Sonnet, Meshy)
6. `./gradlew test` läheb läbi, sh uued testid
7. `scripts/test-metrics-stack.sh` läbib edukalt
8. `docs/METRICS.md` on täidetud ja `README.md`-s viidatud

Commit-sõnum: `feat(metrics): add Prometheus + Grafana stack with business and unit-economics dashboard`

---

## Oodatud mõju

Kohe pärast merge'i:
- Sul on **üks vaade**, kust näed kas ettevõte kasvab või mitte (MRR), kas kasum tuleb sisse (unit economics), kas süsteem töötab (product health) ja kas Forge-pipeline tekitab väärtust (Meshy-fallback langemine)
- Saad teha otsuseid andmete, mitte gut-feel'i pealt. Nt: kui Cache Hit Rate on 2 kuud stabiilne 30% peal, on aeg embedding-cache'i v2. Kui Creator-plaan churn on kõrgem kui Maker-plaan, on aeg Creator'i väärtust läbi vaadata.
- Iga Hacker News / Reddit-launch'i järel saad reaalajas näha kas konversioon tuleb. Kui ei tule 48h jooksul, on aeg sisu kohandada.

Pikem-tähtajaliselt — kui sa palkad kedagi või räägid investoritega — see dashboard ongi see "state of the company" vaade, mida iga rahasse ulatuv kõne nõuab.
