import { defineConfig, devices } from '@playwright/test';

/**
 * One command, the whole stack - no manual terminals:
 *
 *   npx playwright test
 *
 * webServer starts the backend (which brings up PostgreSQL itself - see
 * scripts/start-backend.sh) and the React dev server, and only begins the
 * tests once BOTH readiness URLs answer. PostgreSQL -> Spring Boot -> React
 * -> tests (Phase 11 document, section 13).
 */
export default defineConfig({
  testDir: './tests',

  // DB reset runs in scripts/start-backend.sh BEFORE the API boots (Playwright
  // starts webServer before globalSetup). This guard verifies the fresh state.
  globalSetup: './global-setup.ts',

  fullyParallel: true, // Run all tests in parallel.

  forbidOnly: !!process.env.CI, // If true, a test marked with test.only() will cause Playwright to exit with an error code.

  // Retries SHOULD make CI diagnose flaky tests, not conceal them.
  retries: process.env.CI ? 2 : 0, // Retry failed tests up to 2 times in CI.

  reporter: process.env.CI ? 'html' : 'list', // The HTML reporter creates an HTML report of the test results.

  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: [
    {
      // Brings up postgres, waits for pg_isready, then runs the API (e2e profile).
      command: 'bash scripts/start-backend.sh',
      url: 'http://localhost:8080/api/products',
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
    {
      command: 'npm --prefix ../frontend run dev -- --host 127.0.0.1',
      url: 'http://localhost:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});