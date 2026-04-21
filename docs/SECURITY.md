# Security hardening

Ülevaade turvalisuse kihtidest AI-CAD rakenduses.

## Kihtide mudel

| Kiht        | Komponent                          | Fail / asukoht                                   |
| ----------- | ---------------------------------- | ------------------------------------------------ |
| Transport   | TLS (Caddy auto-LetsEncrypt)       | `Caddyfile`                                      |
| Headers     | HSTS, CSP, X-Frame-Options, nosniff| `SecurityConfig.java` — `.headers(...)`          |
| CORS        | Whitelist: `FRONTEND_URL` + canon  | `SecurityConfig#corsConfig`                      |
| Auth        | OAuth2 Google + JWT (24h TTL)      | `JwtService`, `OAuth2SuccessHandler`             |
| Authz       | `@PreAuthorize` / filter rules     | `SecurityConfig#chain`                           |
| Rate limit  | Bucket4j, per-plan tariff          | `RateLimitFilter`                                |
| Idempotency | `X-Idempotency-Key` 24h cache      | `IdempotencyKeyFilter`                           |
| HMAC        | Stripe webhook signature           | `StripeWebhookHmacValidator`                     |
| Input       | Bean Validation + `InputSanitizer` | DTO-d, `InputSanitizer`                          |
| Secrets     | Kubernetes Secret + Sealed Secrets | `helm/ai-cad/templates/secret.yaml`              |
| Scanning    | OWASP dep-check + Trivy            | `build.gradle` + CI pipeline                     |

## Security headers

Vastusesse lisatakse:

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(self)
Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-eval' https://js.stripe.com ...
```

CSP pole nonce-põhine (Angular Material vajab `unsafe-inline`), aga `frame-ancestors 'none'` + `object-src 'none'` + `base-uri 'self'` kaitseb clickjacking'u ja injection'i eest.

## Rate limiting tariffid

| Plaan     | spec/h | generate/h | Märkus                        |
| --------- | ------ | ---------- | ----------------------------- |
| anonymous | 10     | 5          | IP-põhine                     |
| free      | 20     | 10         | seatud `app.ratelimit.*` env  |
| maker     | 100    | 60         | ex-hobi                       |
| pro       | 300    | 200        |                               |
| team      | 10 000 | 10 000     | effective unlimited           |

Kui kasutaja ületab, tagastame `HTTP 429` koos header'itega:
- `X-RateLimit-Limit: <tier limit>`
- `X-RateLimit-Remaining: 0`
- `Retry-After: <seconds>`

Teha järgmiseks (db-infrastructure branch): `bucket4j-redis` — et horizontaalne skaleerumine ei tooks segadust.

## Idempotency

Klient võib saata `X-Idempotency-Key: <uuid>` header'it POST/PUT/PATCH päringuga. Kui me viimase 24h jooksul oleme seda kombinatsiooni (user+URI+key) näinud, tagastame cached vastuse ja märgime response header'iga `X-Idempotent-Replay: true`. See kaitseb topelttasu ja duplicate-order'ite eest.

**Scope**: key on kitsendatud user/IP + HTTP meetodi + URI järgi. Erinevate kasutajate sama key = erinev cache.

## Stripe webhook HMAC

`POST /api/stripe/webhook` kontrollib `Stripe-Signature` header'it:

```java
if (!StripeWebhookHmacValidator.verify(header, rawBody, webhookSecret)) {
    return ResponseEntity.status(401).build();
}
```

Kasutab constant-time võrdlust ja nõuab timestamp'i viimasest 5 minutist (replay-kaitse).

## OWASP dependency-check

```bash
cd backend && ./gradlew dependencyCheckAnalyze
```

Fail'ib build'i, kui mõni sõltuvus CVSS ≥ 7.0. Raport: `backend/build/reports/dependency-check-report.html`.

CI upload'ib SARIF raporti GitHub Security tab'i — alert'id ilmuvad automaatselt repo Security → Code scanning'is.

False-positive'id märgi `backend/dependency-check-suppressions.xml` faili.

## Secrets

Prod'is:
- **External Secrets Operator** + AWS Secrets Manager / Vault
- **Sealed Secrets** (Bitnami) kui on on-prem k8s
- **Ära** hoia secret'e `values.yaml` sees plain text'ina

Lokaalses: `.env` failis (gitignore'is).

Roadmap: lisa `kubernetes-external-secrets` manifest helm chart'i.

## SSRF-kaitse

`InputSanitizer.isPublicHttpUrl(url)` lükkab tagasi:
- `localhost`, `127.0.0.0/8`
- RFC1918 private ranges (`10.*`, `172.16-31.*`, `192.168.*`)
- Link-local (`169.254.*`, `fe80::*`)
- Docker / cloud metadata (`169.254.169.254`)

Kasuta kõigile välja-minevatele URL-idele, mille kasutaja on kontrollinud (nt partner-API endpoint'id, kui need on seadistatavad).

## Audit log

Kasutaja-tegevused logitakse eraldi tabelisse (`audit_log`, Flyway V5) — roadmap järgmises branch'is.

## Tulevased täiendused

- [ ] Sealed Secrets / External Secrets Operator Helm chart'i
- [ ] CAPTCHA (hCaptcha) anonüümsetele prompt'idele (bot-kaitse)
- [ ] Audit log tabel + controller
- [ ] Refresh token rotation (praegu 24h JWT, pole refresh)
- [ ] CSP nonce-based (eemaldame `'unsafe-inline'`)
- [ ] Pre-commit secret detection (Gitleaks hook)
