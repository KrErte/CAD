import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright konfiguratsioon — smoke + põhivoog testid TehisAI CAD-le.
 *
 * Jooksuta:
 *   npm run e2e:install       # Chromium kohalik download (1x)
 *   npm run e2e               # Kõik testid, headless
 *   npm run e2e:ui            # Interaktiivne UI mode (debugimiseks)
 *
 * CI-s: baseURL = http://localhost:4200, backend peab olema käivitatud eraldi.
 * Lokaalselt: kui baseURL pole seatud, käivitab `npm start` automaatselt.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'et-EE',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: process.env.CI ? undefined : {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
