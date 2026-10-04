import { test, expect, storageStatePathFor } from '../../fixtures/auth.fixture';
import { PlatformAiAssistantPage } from '../../pages/PlatformAiAssistantPage';
import { AnalyticsQueryPage } from '../../pages/AnalyticsQueryPage';
import { AdminAiConfigPage } from '../../pages/AdminAiConfigPage';
import { installPlatformWorkbenchMocks } from '../../support/platform-workbench-mock';
import { installAnalyticsAppMocks } from '../../support/analytics-app-mock';
import { installAdminAppMocks } from '../../support/admin-app-mock';

test.describe('@seed-smoke platform shell smoke', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('platform workbench should expose seeded todo and favorite shortcuts', async ({ page, authUrls }) => {
    await installPlatformWorkbenchMocks(page, {
      todos: [
        {
          type: 'QUALITY',
          title: 'Seed smoke: ERP 可见性检查',
          status: 'ready',
          createdAt: '2026-03-09T09:30:00Z',
          taskId: 'SEED-ERP-001',
          message: '平台 smoke 使用的最小待办数据已就绪。',
        },
      ],
      favorites: [
        {
          id: 'seed-platform-favorite',
          title: 'Seed 平台入口',
          targetType: 'LINK',
          targetId: 'seed-platform',
          link: '/dashboard/workbench',
          sortOrder: 1,
          enabled: true,
        },
      ],
    });

    const workbench = new PlatformAiAssistantPage(page);
    await workbench.goto(authUrls.expert);
    await workbench.expectTodo('Seed smoke: ERP 可见性检查');
    await workbench.expectFavoriteVisible('Seed 平台入口');
  });
});

test.describe('@seed-smoke analytics shell smoke', () => {
  test.use({ storageState: storageStatePathFor('analytics') });

  test('analytics dashboards should expose seeded dashboard card', async ({ page, authUrls }) => {
    await installAnalyticsAppMocks(page);

    const analyticsPage = new AnalyticsQueryPage(page);
    await analyticsPage.gotoDashboards(authUrls.analytics);
    await analyticsPage.expectDashboardVisible('77', '客户销售经营看板');
  });
});

test.describe('@seed-smoke admin shell smoke', () => {
  test.use({ storageState: storageStatePathFor('admin') });

  test('admin infra settings should expose seeded dbt configuration', async ({ page, authUrls }) => {
    await installAdminAppMocks(page);

    const adminPage = new AdminAiConfigPage(page);
    await adminPage.goto(authUrls.admin);
    await adminPage.switchTo('dbt');
    await adminPage.expectFieldValue('dbt', 'DBT 地址', 'http://dbt-service:8080');
    await expect(page.getByTestId('admin-infra-service-panel-dbt')).toBeVisible();
  });
});
