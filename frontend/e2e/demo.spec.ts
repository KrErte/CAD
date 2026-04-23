import { test, expect } from '@playwright/test';

test.describe('Demo widget', () => {
  test('widget is visible on landing page', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.demo-widget')).toBeVisible();
    await expect(page.locator('.demo-badge')).toContainText('DEMO');
    await expect(page.locator('.demo-counter')).toContainText(/\d+\/\d+ left today/);
  });

  test('demo prompt input is available', async ({ page }) => {
    await page.goto('/');
    const input = page.locator('.demo-prompt');
    await expect(input).toBeVisible();
    await expect(input).toHaveAttribute('placeholder', /Try:/);
  });
});
