import { expect, test, storageStatePathFor } from '../../fixtures/auth.fixture';
import { loginToProtectedRoute } from '../../flows/auth-flow';

test('unauthenticated analytics should redirect to auth login with returnUrl', async ({ page, authUrls }) => {
  await page.goto(authUrls.analytics, { waitUntil: 'domcontentloaded' });

  await page.waitForURL(/\/auth\/login\?returnUrl=%2Fanalytics%2F/, { timeout: 15_000 });
  await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Fanalytics%2F/);
});

test('analytics login should return to analytics shell', async ({ page, authUrls }) => {
  await loginToProtectedRoute(page, {
    targetUrl: authUrls.analytics,
    username: 'opadmin',
    password: 'sa',
    expectedReturnPath: '/analytics/',
  });

  await expect(page.getByTestId('app-shell')).toContainText('analytics');
});

test('admin login should return to admin shell', async ({ page, authUrls }) => {
  await loginToProtectedRoute(page, {
    targetUrl: authUrls.admin,
    username: 'sysadmin',
    password: 'sa',
    expectedReturnPath: '/admin/',
  });

  await expect(page.getByTestId('app-shell')).toContainText('admin');
});

test.describe('pre-authenticated routes', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('platform storageState should open expert directly', async ({ page, authUrls }) => {
    await page.goto(authUrls.expert, { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/expert\/?$/);
    await expect(page.getByTestId('app-shell')).toContainText('expert');
  });
});

test.describe('analytics pre-authenticated routes', () => {
  test.use({ storageState: storageStatePathFor('analytics') });

  test('analytics storageState should open analytics directly', async ({ page, authUrls }) => {
    await page.goto(authUrls.analytics, { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/analytics\/?$/);
    await expect(page.getByTestId('app-shell')).toContainText('analytics');
  });
});

test.describe('admin pre-authenticated routes', () => {
  test.use({ storageState: storageStatePathFor('admin') });

  test('admin storageState should open admin directly', async ({ page, authUrls }) => {
    await page.goto(authUrls.admin, { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/admin\/?$/);
    await expect(page.getByTestId('app-shell')).toContainText('admin');
  });
});
