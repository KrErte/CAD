import { test, expect } from '@playwright/test';

test.describe('Pricing page', () => {
  test('page loads with 3 tabs', async ({ page }) => {
    await page.goto('/pricing');
    await expect(page.locator('h1')).toContainText(/pricing/i);
    const tabs = page.locator('.pricing-tab');
    await expect(tabs).toHaveCount(3);
    await expect(tabs.nth(0)).toContainText('Makers');
    await expect(tabs.nth(1)).toContainText('Print Bureaus');
    await expect(tabs.nth(2)).toContainText('Developers');
  });

  test('Makers tab shows 2 cards', async ({ page }) => {
    await page.goto('/pricing');
    // Makers is default tab
    const cards = page.locator('.pricing-grid .pricing-card');
    await expect(cards).toHaveCount(2);
  });

  test('Bureaus tab shows 4 cards', async ({ page }) => {
    await page.goto('/pricing');
    await page.locator('.pricing-tab', { hasText: 'Print Bureaus' }).click();
    const cards = page.locator('.pricing-grid-4 .pricing-card');
    await expect(cards).toHaveCount(4);
  });

  test('Developers tab shows 3 cards + PAYG chip', async ({ page }) => {
    await page.goto('/pricing');
    await page.locator('.pricing-tab', { hasText: 'Developers' }).click();
    const cards = page.locator('.pricing-grid .pricing-card');
    await expect(cards).toHaveCount(3);
    await expect(page.locator('.pricing-chip')).toContainText('PAYG');
  });

  test('featured plans are highlighted', async ({ page }) => {
    await page.goto('/pricing');
    // Creator should be featured on Makers tab
    await expect(page.locator('.pricing-featured')).toHaveCount(1);
    await expect(page.locator('.pricing-featured .pricing-badge')).toContainText('MOST POPULAR');
  });

  test('FAQ accordion works', async ({ page }) => {
    await page.goto('/pricing');
    const firstFaq = page.locator('.faq-item').first();
    await firstFaq.locator('summary').click();
    await expect(firstFaq).toHaveAttribute('open', '');
  });
});
