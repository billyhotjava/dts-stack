import { defineConfig } from '@playwright/test';
import { testServerOrigin, useTestServer } from './support/storage-state';

function htmlReportDir(): string {
  return process.env.DTS_WEB_E2E_HTML_REPORT_DIR?.trim() || 'reports/html';
}

function artifactsDir(): string {
  return process.env.DTS_WEB_E2E_ARTIFACTS_DIR?.trim() || 'reports/artifacts';
}

function usePlatformDevServer(): boolean {
  return ['1', 'true', 'yes', 'on'].includes(
    String(process.env.DTS_WEB_E2E_WITH_PLATFORM_DEV_SERVER ?? '0').trim().toLowerCase(),
  );
}

function platformDevServerPort(): number {
  return Number(process.env.DTS_WEB_E2E_PLATFORM_DEV_SERVER_PORT || '19334');
}

function platformDevServerUrl(): string {
  const explicit = process.env.DTS_PLATFORM_URL?.trim() || process.env.DTS_BASE_URL?.trim();
  if (explicit) {
    return explicit;
  }
  return `http://127.0.0.1:${platformDevServerPort()}/expert/`;
}

function useAnalyticsDevServer(): boolean {
  return ['1', 'true', 'yes', 'on'].includes(
    String(process.env.DTS_WEB_E2E_WITH_ANALYTICS_DEV_SERVER ?? '0').trim().toLowerCase(),
  );
}

function analyticsDevServerPort(): number {
  return Number(process.env.DTS_WEB_E2E_ANALYTICS_DEV_SERVER_PORT || '19335');
}

function analyticsDevServerUrl(): string {
  const explicit = process.env.DTS_ANALYTICS_URL?.trim();
  if (explicit) {
    return explicit;
  }
  return `http://127.0.0.1:${analyticsDevServerPort()}/analytics/`;
}

function useAdminDevServer(): boolean {
  return ['1', 'true', 'yes', 'on'].includes(
    String(process.env.DTS_WEB_E2E_WITH_ADMIN_DEV_SERVER ?? '0').trim().toLowerCase(),
  );
}

function adminDevServerPort(): number {
  return Number(process.env.DTS_WEB_E2E_ADMIN_DEV_SERVER_PORT || '19336');
}

function adminDevServerUrl(): string {
  const explicit = process.env.DTS_ADMIN_URL?.trim();
  if (explicit) {
    return explicit;
  }
  return `http://127.0.0.1:${adminDevServerPort()}/admin/`;
}

const webServers = [];

if (useTestServer()) {
  webServers.push({
    command: 'node ./support/mock-auth-server.mjs',
    url: `${testServerOrigin()}/healthz`,
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  });
}

if (usePlatformDevServer()) {
  webServers.push({
    command: `pnpm --dir ../../source/dts-platform-webapp dev --host 127.0.0.1 --port ${platformDevServerPort()}`,
    url: platformDevServerUrl(),
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  });
}

if (useAnalyticsDevServer()) {
  webServers.push({
    command: `VITE_CACHE_DIR=.vite-cache pnpm --dir ../../source/dts-analytics-webapp/modern exec vite --host 127.0.0.1 --port ${analyticsDevServerPort()} --strictPort`,
    url: analyticsDevServerUrl(),
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  });
}

if (useAdminDevServer()) {
  webServers.push({
    command: `VITE_CACHE_DIR=.vite-cache pnpm --dir ../../source/dts-admin-webapp exec vite --host 127.0.0.1 --port ${adminDevServerPort()} --strictPort`,
    url: adminDevServerUrl(),
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  });
}

export default defineConfig({
  testDir: './specs',
  globalSetup: './global.setup.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  reporter: [['list'], ['html', { open: 'never', outputFolder: htmlReportDir() }]],
  outputDir: artifactsDir(),
  use: {
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    ignoreHTTPSErrors: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        browserName: 'chromium',
        channel: 'chrome',
      },
    },
  ],
  webServer: webServers.length > 0 ? webServers : undefined,
});
