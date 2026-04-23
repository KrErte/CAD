/**
 * k6 smoke test — kinnitab, et süsteem tuleb ühe request'iga toime.
 *
 * Jookse lokaalselt:
 *   k6 run load-tests/smoke.js
 *
 * Parameetrid CLI'st:
 *   k6 run -e BASE_URL=https://staging.krerte.ee load-tests/smoke.js
 */
import http from 'k6/http';
import { check, group } from 'k6';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    // Smoke: kõik peavad passima, p95 < 500ms
    http_req_failed:    ['rate<0.01'],
    http_req_duration:  ['p(95)<500'],
    checks:             ['rate>0.99'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  group('health', () => {
    const r = http.get(`${BASE}/actuator/health`);
    check(r, {
      'status 200':       (r) => r.status === 200,
      'status=UP':        (r) => r.json('status') === 'UP',
      'response < 200ms': (r) => r.timings.duration < 200,
    });
  });

  group('landing', () => {
    const r = http.get(`${BASE}/`);
    check(r, {
      'landing 200': (r) => r.status === 200,
      'CSP header':  (r) => r.headers['Content-Security-Policy'] !== undefined,
      'HSTS header': (r) => r.headers['Strict-Transport-Security'] !== undefined,
    });
  });
}
