import { test, expect, storageStatePathFor } from '../../fixtures/reset.fixture';
import { PlatformAiAssistantPage } from '../../pages/PlatformAiAssistantPage';
import { installPlatformAiModelingMocks } from '../../support/platform-ai-modeling-mock';

test.describe('platform ai modeling flow', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should surface approved AI modeling task and open dbt workspace from workbench', async ({
    page,
    authUrls,
    checkpoints,
  }) => {
    await installPlatformAiModelingMocks(page, { scenario: 'approve' });

    const workbench = new PlatformAiAssistantPage(page);
    await workbench.goto(authUrls.expert);
    await workbench.expectTodo('AI 建模审批待处理');
    await workbench.expectFavoriteVisible('销售域模型工作区');
    await checkpoints.mark('ai-modeling:approval-visible');

    await workbench.openFavorite('销售域模型工作区');
    await expect(page).toHaveURL(/\/dashboard\/modeling\/dbt-files/);
    await checkpoints.mark('ai-modeling:workspace-opened');
  });

  test('should keep draft modeling shortcut visible when approval is not submitted', async ({
    page,
    authUrls,
    checkpoints,
  }) => {
    await installPlatformAiModelingMocks(page, { scenario: 'cancel' });

    const workbench = new PlatformAiAssistantPage(page);
    await workbench.goto(authUrls.expert);
    await workbench.expectTodo('建模方案待确认');
    await expect(page.getByText('AI 建模审批待处理')).toHaveCount(0);
    await workbench.expectFavoriteVisible('销售域建模草稿');

    await workbench.openCreateFavorite();
    await workbench.fillFavoriteForm({
      title: '待提交建模方案',
      targetType: 'MODEL',
      targetId: 'sales-plan-draft',
      link: '/dashboard/modeling/dbt-files',
      sortOrder: '2',
    });
    await workbench.saveFavorite();
    await workbench.expectFavoriteVisible('待提交建模方案');
    await checkpoints.mark('ai-modeling:draft-visible');
  });
});
