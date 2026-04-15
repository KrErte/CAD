# AI-CAD (PoC)

Eesti AI-põhine CAD-teenus. Klient kirjeldab detaili eesti keeles → Claude API parsib
parameetrid → CadQuery genereerib parameetrilise STL-i → kasutaja laeb alla ja saadab
valitud 3D-print-teenusele.

MVP-s on ühe template'iga: **riiuliklamber** (shelf bracket). Laiendamine on parameetri
küsimus — lisa uus template worker'isse ja see tuleb API-st automaatselt välja.

## Stack

- **Backend**: Spring Boot 3 (Java 21) — REST API, töö järjekord
- **Worker**: Python 3 + CadQuery — STL genereerimine
- **AI**: Anthropic Claude API — eesti keel → JSON parameetrid
- **Frontend**: Angular 18 + three.js (STL preview)
- **Deploy**: Docker Compose (lokaalne), k8s manifestid tulevad hiljem

## Käivitamine

```bash
cp .env.example .env   # sisesta ANTHROPIC_API_KEY
docker compose up --build
```

Avaneb http://localhost:4200

## Arhitektuur

```
  Angular (4200)  →  Spring Boot (8080)  →  Python worker (8000)
                                     ↓
                            Claude API (NL → params)
                                     ↓
                            CadQuery → STL
```

## Järgmised sammud

- [ ] Parameetriline template'ide galerii (klamber, konks, kast, adapter)
- [ ] Foto → 3D (Meshy/Tripo API fallback)
- [ ] Kasutajakontod, tellimuste ajalugu
- [ ] Partnerite API (3DKoda, 3DPrinditud) — hinnapäring otse
- [ ] Printability check (overhang, wall thickness)
- [ ] k8s deployment + CI/CD (GitHub Actions)
