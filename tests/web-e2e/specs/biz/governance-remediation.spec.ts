import { test, storageStatePathFor } from '../../fixtures/reset.fixture';
import { GovernancePage } from '../../pages/GovernancePage';
import { PlatformAiAssistantPage } from '../../pages/PlatformAiAssistantPage';
import { installPlatformGovernanceRemediationMocks } from '../../support/platform-governance-remediation-mock';

test.describe('platform governance remediation flow', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should surface anomaly, mark remediation and render ai review result', async ({ page, authUrls, checkpoints }) => {
    await installPlatformGovernanceRemediationMocks(page);

    const governance = new GovernancePage(page);
    await governance.goto(authUrls.expert);
    await governance.selectDataset('ERP 销售订单');
    await governance.startProfile();
    await governance.expectProfileAlert('发现 1 个异常');
    await checkpoints.mark('governance:alert-visible');

    await governance.expectAnomalySeverity('anom-1', 'HIGH');
    await governance.remediateAnomaly('anom-1');
    await governance.expectAnomalyStatus('anom-1', 'RESOLVED');
    await checkpoints.mark('governance:remediation-applied');

    const assistant = new PlatformAiAssistantPage(page);
    await assistant.open();
    await assistant.startNewSession();
    await assistant.sendPrompt('请复核折扣率异常是否已经修复');
    await assistant.expectAssistantMessage('AI 复核结论');
    await checkpoints.mark('governance:ai-review-rendered');
  });
});
