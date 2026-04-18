import { test, expect } from '@playwright/test';

/**
 * Landing page smoke test — kontrollib, et kõik peamised sektsioonid on leheküljel
 * enne kui kasutaja üldse logib sisse. Need on meie müügipind — peavad alati
 * töötama.
 */
test.describe('Landing page — avalik pind', () => {
  test('hero laadub + Darwin animatsioon töötab', async ({ page }) => {
    await page.goto('/');

    // Hero
    await expect(page.locator('h1.hero-title')).toBeVisible();
    await expect(page.locator('.hero-title')).toContainText(/Üks lause|Kirjelda/);

    // Darwin-demo animatsioon peab tootma vähemalt 4 varianti
    await page.waitForTimeout(500); // anna heroRotate() aega jooksutada
    const variants = page.locator('.hero-variant');
    await expect(variants).toHaveCount(6);
  });

  test('navigeerimine sektsioonide vahel töötab', async ({ page }) => {
    await page.goto('/');

    // Nav-linkide loetelu
    const navLinks = ['Kuidas?', 'Darwin CAD', 'Näited', 'Hinnad', 'KKK'];
    for (const text of navLinks) {
      await expect(page.locator('nav.header-nav').getByRole('link', { name: text })).toBeVisible();
    }
  });

  test('use-case kaardid kuvatakse ja kolm on featured', async ({ page }) => {
    await page.goto('/');
    const cards = page.locator('.usecase-card');
    await expect(cards).toHaveCount(3);
    await expect(page.locator('.usecase-pain').first()).not.toBeEmpty();
  });

  test('hinnastik näitab 4 tier-i + kuu/aasta lüliti', async ({ page }) => {
    await page.goto('/#pricing');
    await expect(page.locator('.pricing-grid-4 .pricing-card')).toHaveCount(4);
    await expect(page.locator('.pricing-toggle').filter({ hasText: 'Kuu' })).toBeVisible();
    await expect(page.locator('.pricing-toggle').filter({ hasText: 'Aasta' })).toBeVisible();
  });

  test('kuu → aasta lüliti muudab kuvatavaid hindu', async ({ page }) => {
    await page.goto('/#pricing');
    const popularCard = page.locator('.pricing-popular');
    const monthlyPrice = await popularCard.locator('.pricing-price').textContent();
    expect(monthlyPrice).toContain('29.99');

    await page.locator('.pricing-toggle').filter({ hasText: 'Aasta' }).click();
    await page.waitForTimeout(150);
    const yearlyPrice = await popularCard.locator('.pricing-price').textContent();
    expect(yearlyPrice).toContain('24.90');
  });

  test('FAQ avaneb klikkides', async ({ page }) => {
    await page.goto('/#faq');
    const firstFaq = page.locator('.faq-item').first();
    await firstFaq.locator('summary').click();
    await expect(firstFaq).toHaveAttribute('open', '');
  });

  test('Darwin sektsioon on olemas ja näitab tühja oleku', async ({ page }) => {
    await page.goto('/#darwin');
    await expect(page.locator('#darwin h2')).toContainText(/Darwin CAD/);
    // Ilma sisseloginuta peab empty state olemas olema
    await expect(page.locator('.darwin-empty')).toBeVisible();
  });

  test('Freeform sektsioon on olemas', async ({ page }) => {
    await page.goto('/#expert');
    await expect(page.locator('#expert h2')).toContainText(/Freeform/);
    await expect(page.locator('.expert-editor')).toBeVisible();
    await expect(page.locator('button', { hasText: 'Laadi näidis' })).toBeVisible();
  });

  test('SEO: meta tagid + canonical + OG', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/TehisAI CAD/);
    const ogTitle = await page.locator('meta[property="og:title"]').getAttribute('content');
    expect(ogTitle).toContain('TehisAI');
    const canonical = await page.locator('link[rel="canonical"]').getAttribute('href');
    expect(canonical).toBe('https://tehisaicad.ee/');
  });
});
