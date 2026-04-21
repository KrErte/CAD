/**
 * k6 realistlik load-mix — simuleerib tegelikku kasutaja-liiklust.
 *
 * Ramp-up 5 min -> 50 VU, steady 10 min, ramp-down 2 min.
 * Eesmärk: leida, kus läheb p95 > 1s ja kas Hikari pool saturateerub.
 *
 * Jookse:
 *   k6 run --out json=run.json load-tests/api-mix.js
 *   k6 cloud load-tests/api-mix.js       # k6 Cloud jaoks
 */
import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

const specLatency = new Trend('spec_latency_ms');
const generateLatency = new Trend('generate_latency_ms');
const promptErrors = new Counter('prompt_errors');

export const options = {
  scenarios: {
    ramping_users: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '5m',  target: 50 },  // ramp up
        { duration: '10m', target: 50 },  // steady state
        { duration: '2m',  target: 0 },   // ramp down
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed:    ['rate<0.02'],       // < 2% HTTP vigu
    http_req_duration:  ['p(95)<1000'],      // p95 < 1s
    spec_latency_ms:    ['p(95)<800'],       // Claude round-trip
    generate_latency_ms:['p(95)<5000'],      // worker + slicer
  },
};

const prompts = new SharedArray('prompts', () => [
  '20mm kuup',
  '50mm laiune karp',
  'silinder kõrgusega 30mm',
  'mutter M8 jaoks',
  'konks riidepuu jaoks',
  'lille-pott läbimõõduga 100mm',
  'nööbi-hoidja 8 nööbile',
]);

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const prompt = prompts[Math.floor(Math.random() * prompts.length)];

  group('spec', () => {
    const res = http.post(`${BASE}/api/spec`,
      JSON.stringify({ prompt }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    specLatency.add(res.timings.duration);
    const ok = check(res, {
      'spec 200':         (r) => r.status === 200,
      'spec has template': (r) => r.json('template') !== undefined,
    });
    if (!ok) promptErrors.add(1);
  });

  sleep(Math.random() * 3 + 2); // 2-5s "mõtlemis-aega"

  group('health (side-channel)', () => {
    const r = http.get(`${BASE}/actuator/health`);
    check(r, { 'up': (r) => r.json('status') === 'UP' });
  });

  sleep(Math.random() * 5 + 5); // 5-10s kuni järgmise sessiooni
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data),
    'load-tests/summary.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data) {
  // Lühike kokkuvõte CI log'i jaoks. Põhi-raport on JSON failis.
  const m = data.metrics;
  const p95 = (name) => m[name]?.values?.['p(95)']?.toFixed(0) ?? 'n/a';
  return `
  Load test summary
  ─────────────────
  HTTP p95:            ${p95('http_req_duration')} ms
  spec p95:            ${p95('spec_latency_ms')} ms
  generate p95:        ${p95('generate_latency_ms')} ms
  error rate:          ${(m.http_req_failed?.values?.rate * 100 ?? 0).toFixed(2)}%
  iterations:          ${m.iterations?.values?.count ?? 0}
  prompt_errors:       ${m.prompt_errors?.values?.count ?? 0}
  `;
}
