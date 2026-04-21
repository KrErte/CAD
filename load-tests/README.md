# Load tests (k6)

## Mis siin on

| Fail              | Scenario         | Eesmärk                                       |
| ----------------- | ---------------- | --------------------------------------------- |
| `smoke.js`        | 1 VU × 30s       | Sanity check — kas süsteem jookseb            |
| `api-mix.js`      | ramp 50 VU × 17m | Realistlik kasutaja-mix, leia bottleneck'id   |

## Install

```bash
# macOS
brew install k6

# Ubuntu
sudo snap install k6

# Windows
choco install k6
```

## Jooksuta

```bash
# Lokaalne smoke (docker compose up tööle esimest)
k6 run load-tests/smoke.js

# Staging'i vastu
k6 run -e BASE_URL=https://staging.krerte.ee load-tests/smoke.js

# Realistlik load
k6 run --out json=run.json load-tests/api-mix.js
```

## SLO / thresholds

Me rakendame järgmisi thresholds'e otse skriptides. Kui CI jookseb need alla,
release'i ei tehta.

| Metric               | Target  | Miks                                |
| -------------------- | ------- | ----------------------------------- |
| HTTP error rate      | < 2%    | Rohkem = uuri enne prod'i           |
| HTTP p95             | < 1s    | UI jääb responsive                  |
| spec (Claude) p95    | < 800ms | Anthropic API latency budget        |
| generate (STL) p95   | < 5s    | Worker + slicer end-to-end          |

## GitHub Actions integratsioon

Eraldi workflow `/.github/workflows/load-test.yml` jookseb `workflow_dispatch`
pealt ja push'ib tulemused artifacts'i. ÄRGE pange load-testi iga PR peale —
see on eraldi kohustus, kui muutub latency-kriitiline kood.

## Mis tulevikus lisada

- [ ] `stress.js` — leia breaking point (VU = 500+)
- [ ] `soak.js` — 2h steady load (mälu-leke kontroll)
- [ ] Grafana k6 output: `-o experimental-prometheus-rw` streamib tulemused
      meie Prometheus'i, näeme samas dashboard'is
