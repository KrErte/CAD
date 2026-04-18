import { test, expect } from '@playwright/test';

/**
 * Backend API smoke-test — ilma UI-ta, otse /api/ vastu.
 * Eeldab, et backend jookseb: docker compose up või ./gradlew bootRun
 */
const API = process.env.API_BASE_URL || 'http://localhost:8080';

test.describe('API smoke', () => {
  test('GET /api/health tagastab 200', async ({ request }) => {
    const res = await request.get(`${API}/api/health`);
    expect(res.status()).toBe(200);
  });

  test('GET /api/templates sisaldab vähemalt 20 malli', async ({ request }) => {
    const res = await request.get(`${API}/api/templates`);
    if (res.status() === 404) {
      test.skip(true, '/api/templates pole avalik — katseta läbi proxy');
    }
    const body = await res.json();
    const keys = Object.keys(body);
    expect(keys.length).toBeGreaterThanOrEqual(20);
  });

  test('POST /api/freeform/generate ilma auth-ita tagastab 401', async ({ request }) => {
    const res = await request.post(`${API}/api/freeform/generate`, {
      data: { code: 'import cadquery as cq\nresult = cq.Workplane("XY").box(10,10,10)' }
    });
    expect([401, 403]).toContain(res.status());
  });

  test('POST /api/evolve/seed ilma prompti tagastab 400', async ({ request }) => {
    const res = await request.post(`${API}/api/evolve/seed`, { data: {} });
    // 401 = auth nõutakse kõigepealt, 400 = prompt puudub
    expect([400, 401, 403]).toContain(res.status());
  });
});
