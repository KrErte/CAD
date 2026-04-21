# Architecture Decision Records

ADR = lühike dokument, mis kirjeldab **üht** olulist tehnilist otsust:
konteksti, variante, valikut ja tagajärgi.

## Miks ADR-d?

Kuu aja pärast keegi (tihti sina ise) loeb kood'i ja küsib: **miks X, mitte Y?**
ADR annab sellele otse vastuse. Ilma ADR-ideta peab iga kord uuesti
kaaluma — ja valima vahel valesti.

## Kuidas kirjutada

1. Kopeeri `0000-template.md` → `NNNN-short-title.md`
2. Täida: kontekst, variandid, otsus, tagajärjed
3. Sea **status**: `proposed` → (pärast review'ut) `accepted`
4. PR'i koos koodi-muudatusega, mis otsust rakendab

Kui otsus **muutub** (nt me lähme Redis'elt KeyDB'le), ära kustuta vana ADR'i.
Loo uus, sea vana `deprecated by ADR-NNNN`. Ajalugu jääb.

## Kehtivad ADR-d

| ID       | Pealkiri                                          | Status   |
| -------- | ------------------------------------------------- | -------- |
| [0001](./0001-spring-boot-backend.md) | Spring Boot backend              | accepted |
| [0002](./0002-cadquery-worker-python.md) | CadQuery Python worker       | accepted |
| [0003](./0003-pgvector-semantic-cache.md) | pgvector semantic cache     | accepted |
| [0004](./0004-kubernetes-helm.md) | Kubernetes + Helm                   | accepted |
| [0005](./0005-opentelemetry-observability.md) | OpenTelemetry + Grafana | accepted |

## Viited
- [Michael Nygard'i originaal blog post](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [adr-tools](https://github.com/npryce/adr-tools) — CLI, mis automatise'ib
  neid fail'e (proovi `adr new "kasutame X-i"`)
