import { expect, test, storageStatePathFor } from '../../fixtures/auth.fixture';
import { AnalyticsQueryPage } from '../../pages/AnalyticsQueryPage';
import { installAnalyticsAppMocks } from '../../support/analytics-app-mock';

test.describe('analytics publish flow', () => {
  test.use({ storageState: storageStatePathFor('analytics') });

  test('should filter dashboard, open detail and create share link from current analytics shell', async ({
    page,
    authUrls,
  }) => {
    await installAnalyticsAppMocks(page);

    const queryPage = new AnalyticsQueryPage(page);
    await queryPage.gotoDashboards(authUrls.analytics);
    await page.getByTestId('analytics-dashboard-search').fill('客户销售');
    await queryPage.expectDashboardVisible('77', '客户销售经营看板');

    await queryPage.openDashboard('77');
    await queryPage.expectDashboardDetail('客户销售经营看板');
    await queryPage.createShareLink();
    await queryPage.expectShareLink();

    await page.reload({ waitUntil: 'domcontentloaded' });
    await queryPage.expectDashboardDetail('客户销售经营看板');
    await page.getByTestId('analytics-dashboard-share').click();
    await queryPage.expectShareLink();
    await expect(page).toHaveURL(/\/analytics\/dashboards\/77/);
  });
});
