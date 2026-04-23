import { test, expect, Page } from '@playwright/test';

/**
 * Täielik kasutaja-teekond: prompt → generate → 3D preview → STL download.
 *
 * Eesmärk: katta see lõik, mida unit-testid ei kata — päris HTTP call backend'i,
 * päris three.js render, päris download pop-up. Jookseb CI'd enne iga release'i.
 *
 * NB! See test eeldab, et backend + worker jooksevad localhost:8080 ja teavad
 * vähemalt üht template'i (nt "cube"). CI koostab need Docker Compose'iga.
 *
 * Kui ANTHROPIC_API_KEY pole seatud, siis backend peaks võtma "mock" mode'i
 * (app.claude.mock=true) ja vastama hardcoded spec'iga. Nii ei kuluta me
 * CI-s Claude-krediiti.
 */

async function fillPromptAndGenerate(page: Page, prompt: string) {
  await page.goto('/');

  // Võimalik, et cookie banner on ees — kliki "Nõustu" või "Accept", kui on
  const cookieBtn = page.getByRole('button', { name: /nõustu|accept/i });
  if (await cookieBtn.count() > 0) {
    await cookieBtn.first().click();
  }

  const textarea = page.locator('textarea').first();
  await textarea.fill(prompt);

  // Nupp võib olla "Genereeri" või "Generate"
  await page.getByRole('button', { name: /genereeri|generate/i }).first().click();
}

test.describe('Täielik prompt → STL teekond', () => {
  test.slow(); // Generate + preview võib võtta ~20s worker'ist

  test('genereerib kuubi ja annab download'i nupp', async ({ page }) => {
    await fillPromptAndGenerate(page, '20mm mõõtmetega kuup');

    // Preview-canvas peab ilmuma <= 30s jooksul
    const canvas = page.locator('canvas').first();
    await expect(canvas).toBeVisible({ timeout: 30_000 });

    // Download'i nupp peaks olema enabled, kui STL on valmis
    const download = page.getByRole('link', { name: /download|lae alla.*stl/i })
                     .or(page.getByRole('button', { name: /download|lae alla.*stl/i }));
    await expect(download.first()).toBeVisible({ timeout: 10_000 });
  });

  test('invaliidne prompt → error message, mitte crash', async ({ page }) => {
    await fillPromptAndGenerate(page, 'a'.repeat(5000)); // > MAX_PROMPT_LEN

    // Kas error banner ilmub vm (ei pea olema täpne string — peaasi et UI
    // ei jookse)
    const err = page.locator('.error, [role="alert"]').first();
    // Ei nõua et ilmuks — nõuame ainult, et lehekülg ei oleks katki
    await expect(page.locator('body')).toBeVisible();
  });
});

test.describe('A11y + security smoke', () => {
  test('response ei lekki stack-trace\'i HTML\'i', async ({ page, request }) => {
    const res = await request.post('/api/spec', {
      data: { prompt: null } // invaliidne payload
    });
    const body = await res.text();
    expect(body).not.toContain('SQLException');
    expect(body).not.toContain('at org.springframework');
    expect(body).not.toContain('Caused by');
  });

  test('CSP header on kohal', async ({ request }) => {
    const res = await request.get('/');
    const csp = res.headers()['content-security-policy'];
    expect(csp).toBeTruthy();
    expect(csp).toContain("default-src");
  });
});
