# ADR-0003: pgvector semantic cache Claude API-kulu vähendamiseks

- **Status**: accepted
- **Kuupäev**: 2026-04-15
- **Otsustajad**: @olen-krerte
- **Tehniline keerukus**: M

## Kontekst

Claude API kõne maksab ~0.01€ per spec-generatsioon ja võtab ~400ms. Meil
on hinnanguliselt 60-80% korduvaid promptide ("20mm kuup", "kuubi 20mm"
jne) — sama spec, erinev sõnastus. Kui me saaksime need cache'ida, säästame:
- ~60% Claude-kulu (10k kasutaja peal ~300€/kuu)
- Latency 400ms → 5ms (felt kiirem)

## Variandid

### Variant A: Exact-string Redis cache
- Plussid: lihtne, kiire (sub-ms lookup)
- Miinused: erinev case / sõnajärg = cache miss; hit rate ~20%

### Variant B: pgvector semantic cache
- Plussid: embed prompt → kNN → saavutame ~60%+ hit-rate, ka kui tekst erineb
- Miinused: eeldab embedding API kõnet (~0.0001€/call, 100× odavam kui
  Claude); pgvector extension Postgres'is

### Variant C: Eraldi vector DB (Qdrant / Weaviate / Pinecone)
- Plussid: puhas solution, tiptop performance
- Miinused: veel üks teenus käitama + opsida, pgvector piisab meie skaala
  jaoks (≤ 1M rida)

## Otsus

**Variant B: pgvector semantic cache**. Layer-cake:
1. Redis hash-cache (exact match, 0ms lookup) — 20% hit
2. pgvector kNN (distance < 0.05) — lisaks ~40% hit
3. Claude API call — ainult cold-miss

## Tagajärjed

### Positiivsed
- Arvestuslik kulu-kokkuhoid ~60% Claude budget'ist
- Latency paraneb 95th percentile'is (p95)
- RAG-lite platform tulevikus teiste use-case'ide jaoks (disain-otsing
  "leia sarnane printed design", gallery personalization)

### Negatiivsed
- Embedding-call ise maksab: 1M tokens × 0.0001€ ≈ 0.0001€/call, so
  cache-miss path läheb Claude ~0.01€ + embed 0.0001€ = peaaegu sama
- pgvector extension install vajab pgvector/pgvector:pg16 image'i
  (või `CREATE EXTENSION vector` vajab superuser — meie image'is on)

### Risk'id
- **Risk**: similarity threshold valesti sätitud → vale-cache (return
  wrong spec). **Maandamine**: conservative 0.05 cosine distance = väga
  lähedased promptid; monitooring "cache_wrong_return" metric
  (kui user klõpsab "this isn't what I asked for")
- **Risk**: embedding-API provider (Voyage / OpenAI) rate-limit.
  **Maandamine**: queueing + fallback to Claude-only path

## Viited
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- ADR-0005 distributed tracing — monitoorib cache-path'i
- [Anthropic prompt caching](https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching)
  (eri asi — meie cache on *spec* tasemel, Anthropicu cache on token tasemel)
