# Prompt Claude Code'ile — intent-parsing cache

Kopeeri kogu alljärgnev osa "---" joonte vahelt Claude Code'i.

---

# Ülesanne: lisa Redis-põhine cache ClaudeClient.parseIntent() kutsumiseks

## Konteksts

`ClaudeClient.parseIntent(String prompt)` saadab iga kasutaja-sisendi Claude Haiku mudelile, mis tagastab parametric spec'i (template-nimi + param-väärtused). Iga kutse maksab ~0.002€. Paljud sisendid on semantiliselt identsed aga erinevalt kirjutatud:

- "L-bracket 5kg 4 holes"
- "L bracket, 5 kg, 4 holes"
- "l-bracket   5kg 4 holes."

Need peaksid tagastama sama spec'i ilma Claude'i kutsumata. Eesmärk: **vähendada Haiku-kulu ~50%** normaliseeritud-prompt-hash caching'uga. (Semantic similarity embedding'utega on eraldi iteratsioon — **ära tee seda praegu**.)

## Mida teha

### 1. Loo `IntentCacheService`

Failitee: `backend/src/main/java/com/aicad/claude/IntentCacheService.java`

Interface:

```java
public interface IntentCacheService {
    Optional<Spec> get(String prompt);
    void put(String prompt, Spec spec);
    long invalidate();  // flush kogu cache, tagastab eemaldatud võtmete arv
    CacheStats stats();  // {hits, misses, hitRate, size}
}
```

**Primaarne implementatsioon**: `RedisIntentCacheService` kasutades Spring Data Redis'i (`RedisTemplate<String, Spec>`).

- Cache-võtme-formaat: `intent:{catalogVersion}:{promptHash}`
- `promptHash` = SHA-256(normalized(prompt)) hex-na
- `catalogVersion` = SHA-256(kõikide template-nimede sorteeritud nimekirjast + nende skeemide JSON-ist) — kui template'ite nimekiri muutub, kõik vanad cache-kirjed aeguvad automaatselt (uus võtme-prefiks)
- TTL: 7 päeva (`Duration.ofDays(7)`)
- Redis-võti-prefiks: `intent:` — saab `redis-cli --scan --pattern "intent:*"` abil vaadata ja `FLUSHDB` ei pruugi sobida, kui Redis on jagatud.

**Fallback implementatsioon**: `InMemoryIntentCacheService` kasutades Caffeine'i (`com.github.ben-manes.caffeine:caffeine` — kontrolli kas juba `build.gradle`-is on, kui pole siis lisa).

- Max size: 10 000 kirjet
- Expire: `expireAfterWrite(Duration.ofDays(7))`
- Kui Redis ei käivitu või Redis-operatsioon throw'ib `RedisConnectionFailureException`, logi `WARN` ja fallback'i siia automaatselt. See on olemasolev muster teistes teenustes (vt `UsageTrackingService`, kui see juba loodud on).

**Bean valik `@Configuration`-is**:

```java
@Bean
@ConditionalOnProperty(name = "spring.redis.host")
public IntentCacheService redisIntentCacheService(...) { ... }

@Bean
@ConditionalOnMissingBean(IntentCacheService.class)
public IntentCacheService inMemoryIntentCacheService() { ... }
```

### 2. Prompt-normaliseerimine

Uus utility `PromptNormalizer.java` samas paketis:

```java
public final class PromptNormalizer {
    /**
     * Normaliseeri prompt kindlustamaks, et semantiliselt identsed
     * kirjutused annavad sama hashi:
     *   1. Lowercase
     *   2. Trim
     *   3. Collapse consecutive whitespace → single space
     *   4. Remove punctuation: , . ; : ! ? — - ( ) [ ] { } " ' `
     *   5. Normalize Unicode to NFKC
     *   6. Remove diacritics (ä→a, õ→o) — VÕIMALDAB matchida eesti/inglise varianti kui sama idee
     *
     * NÄITED peaksid andma sama väljundi:
     *   "L-bracket 5kg 4 holes"   → "lbracket 5kg 4 holes"
     *   "L bracket, 5 kg, 4 holes" → "lbracket 5 kg 4 holes"  // MISMATCH number-spacing
     *
     * LISA normaliseerimine:
     *   7. "5kg", "5 kg", "5  kg" → "5kg" (number + unit kleebitud)
     *   8. Unit-normaliseerimine: "mm", "millimeter", "millimeters" → "mm"; "kg", "kilogram" → "kg"
     */
    public static String normalize(String prompt) { ... }

    public static String hash(String normalizedPrompt) {
        // SHA-256 → hex (64 chars)
    }
}
```

**Tähelepanu**: punkt 6 (diacritics) on agressiivne — võib põhjustada, et eesti ja inglise versioon sama promptist jagavad cache'i. See on **tahtlik** — me tahame maksimaalset cache-hit rate'i. Risk: kui Claude tagastab eesti sisendile inglise-label'itega spec'i või vastupidi, võib mismatch'id tekkida. **Ära muretse selle üle praegu** — spec struktuur on language-agnostic (ainult template-name ja param-väärtused numbritena), seega tulemus on õige.

### 3. Integreeri `ClaudeClient.parseIntent()`

Modifitseeri olemasolevat `ClaudeClient.java` (või kus iganes intent-parsing toimub):

```java
public Spec parseIntent(String prompt) {
    Optional<Spec> cached = cacheService.get(prompt);
    if (cached.isPresent()) {
        metrics.counter("intent.cache.hit").increment();
        return cached.get();
    }
    metrics.counter("intent.cache.miss").increment();

    Spec spec = callClaudeHaiku(prompt);  // olemasolev loogika
    cacheService.put(prompt, spec);
    return spec;
}
```

**Tähtis**: kui `callClaudeHaiku()` throw'ib (timeout, rate-limit, auth-error), **ära cache'i** — levita exception edasi. Cache'imine throw'iva tulemuse puhul oleks negatiivne cache ja blokeerib taastumise.

### 4. Lisa cache-stats endpoint

Uus REST endpoint admin-debug jaoks:

`GET /api/admin/intent-cache/stats`

Tagastab:

```json
{
  "backend": "redis",
  "hits": 1243,
  "misses": 491,
  "hitRate": 0.717,
  "size": 821,
  "estimatedSavingsEur": 2.486
}
```

Kus `estimatedSavingsEur = hits * 0.002`. Securi see endpoint `@PreAuthorize("hasRole('ADMIN')")`-ga (või lihtsalt `application.yml`-ist `admin.api-key` header'iga, kui role-süsteemi ei ole veel).

`POST /api/admin/intent-cache/flush` — invaliidib kogu cache'i. Same auth.

Lisa response header'ina `X-Cache: HIT` või `X-Cache: MISS` `/api/generate`-is, et saaks DevTools'is kohe näha.

### 5. Micrometer metrics

Kui Micrometer on juba projektis (`spring-boot-starter-actuator`), registreeri:

- `intent.cache.hit` (counter)
- `intent.cache.miss` (counter)
- `intent.cache.size` (gauge)
- `intent.cache.hit.rate` (gauge) — rolling 5-min window

Kui Prometheus endpoint (`/actuator/prometheus`) on konfigureeritud, peaks need automaatselt exposed saama.

### 6. Testid

**`IntentCacheServiceTest.java`** (integration test):

- `@SpringBootTest` koos embedded Redis'iga (`it.ozimov:embedded-redis` või Testcontainers `RedisContainer`)
- Test: sama prompt kaks korda → teine kord `get()` tagastab `Optional.of(spec)` ilma stub-call'i tegemata
- Test: "L-bracket 5kg 4 holes" ja "l bracket 5 kg 4 holes" annavad sama cache-võtme
- Test: pärast `invalidate()` tagastab `get()` `Optional.empty()`
- Test: Redis-alla-läinud-stsenaarium → automaatselt fallback in-memory'le (mock `RedisConnectionFailureException`)

**`PromptNormalizerTest.java`** (unit test, parameterized):

Kaks massiivi — need promptid peavad andma sama hashi:

```java
@ParameterizedTest
@CsvSource({
    "'L-bracket 5kg 4 holes', 'l bracket 5 kg 4 holes.'",
    "'Konks 3 kg rätikule', 'konks, 3kg rätikule!'",
    "'Shelf bracket — 5 kg load', 'shelf bracket 5kg load'",
    "'cable clamp 3 cables 6mm', 'Cable clamp, 3 cables, 6 mm'"
})
void equivalentPromptsHashEqual(String a, String b) {
    assertEquals(
        PromptNormalizer.hash(PromptNormalizer.normalize(a)),
        PromptNormalizer.hash(PromptNormalizer.normalize(b))
    );
}
```

Ja need peavad andma **erineva** hashi:

```java
@CsvSource({
    "'L-bracket 5kg', 'L-bracket 10kg'",  // erinev koormus
    "'hook 3 holes', 'hook 4 holes'",     // erinev auke-arv
    "'box 10cm', 'box 20cm'"              // erinev mõõt
})
void differentPromptsHashDiffer(String a, String b) { ... }
```

### 7. Dokumentatsioon

Lisa `docs/CACHING.md` lühike failike (max 1 lehekülg):

- Mis cache on, miks
- Kus redis-võtmed asuvad (`intent:*` prefiksiga)
- Kuidas flush'ida (admin endpoint või `redis-cli DEL`)
- TTL-i põhjendus
- Kus vaadata stats'e (admin endpoint + Prometheus metrics)
- Kuidas turvaliselt testida cache-invalidatsiooni (catalog version key)

## Piirangud

- **Ära tee** embedding-põhist semantic similarity'd — see on eraldi iteratsioon, keerulisem ja vajab eraldi provider-valikut (OpenAI embeddings vs local model).
- **Ära puuduta** Design Review endpoint'i (`/api/review`) — see kasutab Sonnet'it ja vision-input'i, mida on raske cache'ida (PNG-hash oleks võtmes, vähe hit'e).
- **Ära cache'i** Meshy-fallback-vastuseid — nende väärtused on suured (GLB binaries) ja kiiresti vananevad.
- **Ära lisa** cache-warming'ut ega background-refreshi — see on overengineering algfaasis.

## Acceptance criteria

1. `./gradlew test` läheb rohelisena läbi, sh uued `IntentCacheServiceTest` ja `PromptNormalizerTest`
2. Lokaalselt käivitatud backend (`docker compose up`) näitab `GET /api/admin/intent-cache/stats` õiget vastust
3. Kaks identset `/api/generate` päringut (nt `curl -d '{"prompt":"L-bracket 5kg"}' ...` kaks korda) — teine tagastab `X-Cache: HIT` header'i
4. Redis'es on pärast mõnda päringut näha võtmeid `redis-cli KEYS "intent:*"`
5. Kui Redis'i alla lülitada (`docker compose stop redis`), backend ei kukku maha — logib `WARN` ja hakkab kasutama in-memory cache'i
6. Prometheus endpoint `/actuator/prometheus` näitab `intent_cache_hit_total` ja `intent_cache_miss_total` meetriid

## Oodatud mõju

Pärast 24h produktsioonis peaks:

- `intent.cache.hit.rate` olema **30–50%** (pole embedding'uid, aga prompt-normalisatsioon katab kõige sagedasemad variandid)
- `estimatedSavingsEur` rida stats'ides näitama positiivset numbrit
- Claude API kulu log'ides langenud proportsionaalselt hit-rate'i võrra

Commit-sõnum: `feat(cache): add intent-parsing cache with Redis + in-memory fallback`
