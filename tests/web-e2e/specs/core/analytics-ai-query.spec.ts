import { test, storageStatePathFor } from '../../fixtures/auth.fixture';
import { AnalyticsQueryPage } from '../../pages/AnalyticsQueryPage';
import { installAnalyticsAppMocks } from '../../support/analytics-app-mock';

test.describe('analytics ai, query and screen', () => {
  test.use({ storageState: storageStatePathFor('analytics') });

  test('should list dashboards and open dashboard detail', async ({ page, authUrls }) => {
    await installAnalyticsAppMocks(page);

    const queryPage = new AnalyticsQueryPage(page);
    await queryPage.gotoDashboards(authUrls.analytics);
    await queryPage.expectDashboardVisible('77', '客户销售经营看板');
    await queryPage.openDashboard('77');
    await queryPage.expectDashboardDetail('客户销售经营看板');
  });

  test('should create dashboard share link from detail page', async ({ page, authUrls }) => {
    await installAnalyticsAppMocks(page);

    const queryPage = new AnalyticsQueryPage(page);
    await queryPage.gotoDashboards(authUrls.analytics);
    await queryPage.openDashboard('77');
    await queryPage.createShareLink();
    await queryPage.expectShareLink();
  });
});
