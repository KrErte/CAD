# ADR-0001: Kasutame Spring Boot 3.3 backend-keelena

- **Status**: accepted
- **Kuupäev**: 2025-09-14
- **Otsustajad**: @olen-krerte
- **Tehniline keerukus**: XL

## Kontekst

AI-CAD vajab backend'i, mis:
- Avaldab REST + WebSocket API
- Integreerub OAuth2 (Google), Stripe, Anthropic API, PostgreSQL
- Skaleerub mitme instantsini (k8s HPA)
- Annab õppevara järgmistele inimestele, kes projekti liituvad (tööturu-käepärasus)

## Variandid, mida kaalusime

### Variant A: Spring Boot 3.x (Java 21)
- Plussid:
  - Küpseim Java ökosüsteem OAuth2 + Actuator + data-JPA jaoks
  - Testcontainers, Flyway, Jacoco jne out-of-the-box
  - Java turuõpe on suur
  - OpenTelemetry / Micrometer esmaklassiline tugi
- Miinused:
  - JVM mäluhulk (~500 MB warm), Node.js on ~80 MB
  - Build-aeg (~30s) on aeglasem kui Go / Node

### Variant B: FastAPI (Python)
- Plussid: ühe keele (Python) kasutamine backend + worker'is
- Miinused: async-põhine, aga OAuth2 + JPA ekvivalent (SQLAlchemy) on
  ebaküpsem. Puudub Spring Security analoog.

### Variant C: Node.js + NestJS (TypeScript)
- Plussid: TypeScript tüübid samad, mis frontend'is
- Miinused: pole nii küps security/observability stack, ORM'id (Prisma,
  TypeORM) on vähem peenhäälestatud Spring Data'ga

## Otsus

**Spring Boot 3.3 + Java 21**. Argumendid Actuator, Security, Data JPA,
Testcontainers küpsuse poolt kaaluvad üles JVM mälu-kulu. Prod'is jookseb
k8s pod 1 GB limit'iga, mis on taskukohane.

## Tagajärjed

### Positiivsed
- Spring Security + JWT + OAuth2 = konfigitava 50 LoC'iga, mitte 500
- Actuator annab terviklik observability (health, metrics, tracing)
  automaatselt
- Testcontainers + Spring Boot integration-testid "lihtsalt töötavad"

### Negatiivsed
- Polyglot stack (Java backend + Python worker + TS frontend) — peame
  3 ökosüsteemis paketti hoidma
- Cold-start ~4s on NoSQL-kiire-function võrreldes aeglane — EI sobi
  lambda-stiilis deploy'miseks, aga sobib k8s Deployment'ile

### Risk'id
- **Risk**: GraalVM native-image täieliku tugi veel puudub Spring Data + Flyway
  kombinatsiooni jaoks. **Maandamine**: JVM mode prod'is, native = proof-of-
  concept later.

## Viited
- [Spring Boot 3.3 release notes](https://github.com/spring-projects/spring-boot/releases)
- Ei lukusta — hiljem saab Quarkus'ile rändluda (sama JAX-RS, sama JPA)
