# Security Policy

## Supported versions

| Versioon | Toetatud          |
| -------- | ----------------- |
| `main`   | :white_check_mark:|
| `< main` | :x:               |

Me jälgime ainult `main` branch'i. Prod'is jookseb alati viimase `v*` tag'i release.

## Reporting a vulnerability

**Ära loo avalikku GitHub issue'i.** Tundlikest haavatavustest teavita privaatselt:

1. **GitHub Security Advisory** — eelistatud tee:
   https://github.com/olen-krerte/CAD/security/advisories/new
2. **E-post**: security@krerte.ee (PGP: vaata `.well-known/security.txt`)

Palun lisa:
- Mõju kirjeldus (mis vargsi juhtuks, kui keegi haavatavust ära kasutaks)
- Reproduktsiooni sammud või PoC
- Mõjutatud versioon(id) või commit SHA
- CVSS hinnang, kui oskad

## Response timeline

| Severity | Acknowledge | Fix target      |
| -------- | ----------- | --------------- |
| Critical | 24h         | 7 päeva         |
| High     | 48h         | 14 päeva        |
| Medium   | 5 päeva     | 30 päeva        |
| Low      | 7 päeva     | järgmises releas'is |

Me avalikustame haavatavuse pärast seda, kui fix on prod'is ja kõik aktiivsed
kliendid on saanud vähemalt 7 päeva patch'imiseks.

## Scope

**In-scope** (tasu coordinated disclosure eest):
- `*.krerte.ee` domeen
- `ghcr.io/olen-krerte/cad-*` container image'id
- Backend REST API (`/api/**`)
- Stripe webhook endpoint (`/api/stripe/webhook`)
- OAuth2 callback flow

**Out-of-scope**:
- Sotsiaal-manipulatsioon meeskonna vastu
- Tehnilised issue'd third-party teenuses (Stripe, Google OAuth, Anthropic API) — raporteeri neile otse
- DoS ilma PoC'ita
- Missing security headers, kui need ei võimalda konkreetset rünnakut (vaata `docs/SECURITY.md` nimekirja)

## Safe harbor

Me ei algata õiguslikku protsessi teadlase vastu, kes järgib seda policy'ut
hea-usu raames: ei riku kasutaja privaatsust, ei hävita andmeid, ei ürita
service'it maha võtta, peatab testimise kohe kui saab piisavalt PoC'i jaoks.
