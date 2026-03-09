import { test, storageStatePathFor } from '../../fixtures/auth.fixture';
import { AnalyticsQueryPage } from '../../pages/AnalyticsQueryPage';
import { DashboardPage } from '../../pages/DashboardPage';
import { ScreenDesignerPage } from '../../pages/ScreenDesignerPage';
import { installAnalyticsAppMocks } from '../../support/analytics-app-mock';

test.describe('analytics publish flow', () => {
  test.use({ storageState: storageStatePathFor('analytics') });

  test('should carry query results into dashboard and keep screen publish state after reload', async ({
    page,
    authUrls,
  }) => {
    await installAnalyticsAppMocks(page);

    const queryPage = new AnalyticsQueryPage(page);
    const dashboardPage = new DashboardPage(page);
    const screenDesigner = new ScreenDesignerPage(page);

    await queryPage.gotoCardDetail(authUrls.analytics, '42');
    await queryPage.expectSql('ads_customer_sales');
    await queryPage.expectResultCell('华东装备');

    await dashboardPage.gotoCreate(authUrls.analytics);
    await dashboardPage.createWithCard('客户销售经营看板', '42');
    await dashboardPage.expectSaved('77');
    await dashboardPage.dashcard('501').getByText('华东装备').waitFor();

    await screenDesigner.gotoEdit(authUrls.analytics, '88');
    await screenDesigner.publish();
    await screenDesigner.expectPublished('v3');

    await page.reload({ waitUntil: 'domcontentloaded' });
    await screenDesigner.expectPublished('v3');
  });
});
