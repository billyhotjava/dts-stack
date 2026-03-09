import { expect, test, storageStatePathFor } from '../../fixtures/auth.fixture';
import { AnalyticsScreenPage } from '../../pages/AnalyticsScreenPage';
import { ScreenDesignerPage } from '../../pages/ScreenDesignerPage';
import { installAnalyticsAppMocks } from '../../support/analytics-app-mock';

test.describe('analytics screen template runtime', () => {
  test.use({ storageState: storageStatePathFor('analytics') });

  test('should create project management screen from builtin template and verify runtime drill flow', async ({
    page,
    authUrls,
  }) => {
    await installAnalyticsAppMocks(page);

    const screenPage = new AnalyticsScreenPage(page);
    await screenPage.gotoScreens(authUrls.analytics);
    await screenPage.openTemplateGallery();

    await screenPage.expectBuiltinTemplate('qms-cockpit', 'QMS质量驾驶舱');
    await screenPage.expectBuiltinTemplate('plm-cockpit', 'PLM研发交付看板');
    await screenPage.expectBuiltinTemplate('hr-cockpit', 'HR人效与培训看板');
    await screenPage.expectBuiltinTemplate('finance-execution-cockpit', '财务执行驾驶舱');
    await screenPage.expectBuiltinTemplate('project-management-cockpit', '项目管理作战台');

    await screenPage.useBuiltinTemplate('project-management-cockpit');

    const designerPage = new ScreenDesignerPage(page);
    await expect(designerPage.root).toBeVisible();
    const screenId = screenPage.currentScreenId();

    await designerPage.publish();
    await designerPage.expectPublished('v1');

    await screenPage.gotoPreview(screenId);
    await expect(screenPage.previewRoot).toContainText('项目管理作战台');

    const riskFilter = screenPage.componentByName('风险筛选').locator('select');
    await riskFilter.selectOption('高');
    await expect(riskFilter).toHaveValue('高');

    const riskTable = screenPage.componentByName('风险与堵点');
    await riskTable.locator('tbody tr').filter({ hasText: 'QMS二期' }).click();

    await expect(screenPage.runtimePanel).toBeVisible();
    await expect(page.getByTestId('analytics-screen-runtime-panel-title')).toContainText('QMS二期 · 高风险');
    await expect(riskTable).toContainText('项目: QMS二期');

    await riskTable.getByText('全部').click();
    await expect(riskTable.getByText('项目: QMS二期')).toHaveCount(0);

    await page.getByTestId('analytics-screen-runtime-panel-close').click();
    await expect(screenPage.runtimePanel).toHaveCount(0);
  });
});
