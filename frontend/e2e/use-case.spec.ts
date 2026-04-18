import { test, expect } from '@playwright/test';

/**
 * Use-case kaartide interaktsioon — klikk peab täitma promptiväli ja
 * scroll'ima app-sektsiooni.
 */
test.describe('Päris-elu kasutusjuhtumid', () => {
  test('klikk use-case kaardile täidab prompti', async ({ page }) => {
    await page.goto('/');
    await page.locator('.usecase-card').first().click();

    // App-sektsioon peab nüüd nähtavale tulla
    await expect(page.locator('#app')).toBeInViewport({ ratio: 0.1 });

    // Prompt tekstikast peab sisaldama tekstiandmeid (fragment matching)
    const textarea = page.locator('textarea').first();
    const val = await textarea.inputValue();
    expect(val.length).toBeGreaterThan(10);
  });

  test('kolm eri use-case kaarti — eri promptid', async ({ page }) => {
    await page.goto('/');
    const cards = page.locator('.usecase-card');
    const count = await cards.count();
    expect(count).toBe(3);

    const prompts: string[] = [];
    for (let i = 0; i < count; i++) {
      await page.goto('/');
      await cards.nth(i).click();
      await page.waitForTimeout(300);
      const val = await page.locator('textarea').first().inputValue();
      prompts.push(val);
    }
    // Kõik 3 prompti peavad olema erinevad
    expect(new Set(prompts).size).toBe(3);
  });
});
