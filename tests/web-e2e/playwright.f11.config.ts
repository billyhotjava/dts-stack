import { defineConfig } from '@playwright/test';

// Deliberately separate from the legacy API-login/storage-state setup.
// F11 browser cases log in through visible pages and never start a mock server.
export default defineConfig({
  testDir: './specs/f11',
  testMatch: '**/*.f11.ts',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  timeout: 180_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'reports/f11/html' }]],
  outputDir: 'reports/f11/artifacts',
  use: {
    browserName: 'chromium',
    headless: false,
    ...(process.env.F11_CHROME_EXECUTABLE
      ? { launchOptions: { executablePath: process.env.F11_CHROME_EXECUTABLE } }
      : { channel: 'chrome' }),
    viewport: { width: 1366, height: 768 },
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    screenshot: 'only-on-failure',
    // Login credentials and certificate interactions must not enter a raw trace/video.
    trace: 'off',
    video: 'off',
  },
});
