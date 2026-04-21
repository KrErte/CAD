# Data infrastructure

Mis jookseb meil "state layer"'is ja miks iga komponent seal on.

## Ülevaade

```
           ┌──────────────┐        ┌──────────────┐
           │   Postgres   │        │     Redis    │
           │   + pgvector │        │ cache + rate │
           └──────────────┘        └──────────────┘
                  │                        │
                  │  JPA / jdbcTemplate    │  RedisCacheManager
                  │                        │
                 ┌┴────────────────────────┴┐
                 │        Backend           │
                 └──────────┬───────────────┘
                            │
                            │  S3 SDK
                            ▼
                    ┌──────────────┐
                    │ MinIO / S3   │
                    │ STL + Gcode  │
                    └──────────────┘
```

## Postgres 16 + pgvector

**Image**: `pgvector/pgvector:pg16` (Postgres 16 koos kompileeritud
`vector` extension'iga)

**Mis läheb sinna**:
- Relational data: users, orders, designs, printers, organizations
- `prompt_embeddings` — 1536-dim vektorid sarnase-prompti RAG-lite cache'ile
- `audit_log` — immutable audit trail
- Kõik Flyway migrat (V1..V6)

**Mis EI lähe sinna enam**:
- STL binaar `bytea` — deprecated V6'st, uus `stl_s3_key` viitab MinIO'le
- Gcode — samuti S3'sse

**pgvector põhikasutus**:

```sql
-- Salvesta embedding
INSERT INTO prompt_embeddings (prompt_hash, prompt_text, embedding, template_name, spec_json)
VALUES ('abc...', '20mm kuup', '[0.12, -0.03, ...]'::vector, 'cube', '{...}');

-- Otsi lähim (cosine distance)
SELECT template_name, spec_json, embedding <=> '[0.1, -0.04, ...]'::vector AS distance
FROM prompt_embeddings
ORDER BY distance
LIMIT 1;
```

`<=>` = cosine distance, `<->` = L2, `<#>` = inner product (negatiivne).

## Redis 7

**Miks**:
1. **Cache** (`claude:spec`, `claude:review`, `partners:prices`) —
   Claude call on ~400ms ja 0.01€. Cache hit tagastab 2ms'iga ja 0€'ga.
2. **Distributed rate limiting** — bucket4j-redis hoiab tariif-state'i
   jagatult. Kui skaleerime 3 pod'ini, siis user ei saa 3× rohkem limit'i.
3. **Tulevikus**: session store, pub/sub (printer-event fan-out)

**Eviction**: `maxmemory 256mb, allkeys-lru` — dev'is. Prod'is Elasti-
Cache / Upstash pakettide järgi.

**Spring config**:
```yaml
spring:
  data:
    redis:
      url: redis://redis:6379
      lettuce:
        pool: { max-active: 16, max-idle: 8, min-idle: 2 }
  cache:
    type: redis
```

## MinIO (S3-compatible)

**Miks mitte AWS S3 otse?**: Dev / CI / on-prem (EKS ei ole kõigile eelarves).
Sama SDK töötab mõlemaga — `app.storage.endpoint` vahetab provider'i.

**Bucket struktuur**:

```
cad-artifacts/              # aktiivsed asjad
  designs/{id}/*.stl
  designs/{id}/preview.png
  gcode/{job_id}.gcode
cad-archive/                # 30+ päeva vanad, cold tier
  audit_log/YYYY-MM.jsonl.gz
  orders/YYYY/{order_id}.pdf
```

**Pre-signed URL pattern**: frontend küsib backend'ilt `GET /api/design/{id}/download`,
backend tagastab `{ "url": "https://minio.../...?X-Amz-Signature=..." }` 1h TTL'iga.
Frontend redirectb download'ile — backend server'it ei koorma.

**Lifecycle policy**: `cad-archive` bucket'i `expire-after 90 päeva` —
compliance jaoks. `cad-artifacts` ei aegu, aga pärast 30 päeva uue access'ita
kolib **Glacier Instant Retrieval** (10× odavam storage).

## Semantic cache flow

Kui kasutaja kirjutab prompt'i, siis teeme selle kaudu:

```
  ┌───────────────┐
  │ user prompt   │
  └──────┬────────┘
         │ 1. SHA256 → Redis cache:spec hash lookup
         ▼
  ┌───────────────┐ hit → return cached spec (2ms, 0€)
  │ Redis hash?   ├─────────────────────────────────►
  └──────┬────────┘
         │ miss
         │ 2. embed(prompt) → pgvector kNN
         ▼
  ┌───────────────┐ dist < 0.05 → return cached spec
  │ pgvector <=>  ├─────────────────────────────────►
  └──────┬────────┘
         │ dist >= 0.05
         │ 3. Claude call (400ms, ~0.01€)
         ▼
  ┌───────────────┐
  │ Claude API    │
  └──────┬────────┘
         │ store (hash + embedding + spec)
         ▼
    both caches updated for next time
```

**Hit rate target**: 60% pärast 1 nädala live liiklust (hinnanguline).
Cache hit metric'id Grafana's — vt `claude.api.cache.hit.count` vs
`claude.api.requests.total`.

## Testcontainers

Integratsiooni-testid käivitavad real:
- Postgres 16 (pgvector läheb Flyway V6'ga sisse automaatselt)
- Redis 7 (cache-testide jaoks)
- MinIO (S3 round-trip testid)

Vt `docs/TESTING.md` detail'ideks.

## Backup plan

| Andmed        | Backup frequency | Retention | RPO | RTO |
| ------------- | ---------------- | --------- | --- | --- |
| Postgres      | WAL ship pidev + daily pg_dump | 30 päeva | 5 min | 30 min |
| MinIO         | versioning ON + weekly replica | 90 päeva | 24h   | 2h |
| Redis         | ei backup'ita (cache)          | — | — | — |

Redis on ephemeraalne — kui läheb maha, cold-start on aeglane aga turvaline
(Claude call'id tagasi 2 päeva jooksul soojendavad cache'i).
