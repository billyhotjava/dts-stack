import { test, expect, storageStatePathFor } from '../../fixtures/reset.fixture';
import { PlatformAiAssistantPage } from '../../pages/PlatformAiAssistantPage';
import { installPlatformGovernanceRemediationMocks } from '../../support/platform-governance-remediation-mock';

test.describe('platform governance remediation flow', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should surface governance remediation tasks and open quality rules shortcut from workbench', async ({
    page,
    authUrls,
    checkpoints,
  }) => {
    await installPlatformGovernanceRemediationMocks(page);

    const workbench = new PlatformAiAssistantPage(page);
    await workbench.goto(authUrls.expert);
    await workbench.expectTodo('客户主数据质量复核');
    await workbench.expectTodo('ERP 销售订单字段漂移提醒');
    await workbench.expectFavoriteVisible('质量规则台账');
    await checkpoints.mark('governance:todos-visible');

    await workbench.openFavorite('质量规则台账');
    await expect(page).toHaveURL(/\/dashboard\/governance\/quality-rules/);
    await checkpoints.mark('governance:quality-rules-opened');
  });
});
