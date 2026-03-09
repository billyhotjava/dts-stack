import { test, storageStatePathFor } from '../../fixtures/reset.fixture';
import { ModelingPage } from '../../pages/ModelingPage';
import { PlatformAiAssistantPage } from '../../pages/PlatformAiAssistantPage';
import { installPlatformAiModelingMocks } from '../../support/platform-ai-modeling-mock';

test.describe('platform ai modeling flow', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should approve ai modeling action and expose generated models in modeling page', async ({ page, authUrls, checkpoints }) => {
    await installPlatformAiModelingMocks(page, { scenario: 'approve' });

    const assistant = new PlatformAiAssistantPage(page);
    await assistant.goto(authUrls.expert);
    await assistant.open();
    await assistant.startNewSession();
    await assistant.sendPrompt('请基于 ERP 销售订单生成销售域 DWD 和 DWS 模型');
    await assistant.expectApprovalCard();
    await assistant.expectToolTraceResult('generate_sql_models', '销售域');
    await assistant.approve();
    await assistant.expectApprovalClosed();
    await checkpoints.mark('ai-modeling:approved');

    const modelingPage = new ModelingPage(page);
    await modelingPage.goto(authUrls.expert);
    await modelingPage.expectActiveModel('dwd_sales_orders');
    await modelingPage.expectSyncStats('模型 +2/~0');
    await modelingPage.expectLatestBuildStatus('SUCCESS');
    await checkpoints.mark('ai-modeling:models-visible');
  });

  test('should cancel ai modeling action and keep modeling space unchanged', async ({ page, authUrls, checkpoints }) => {
    await installPlatformAiModelingMocks(page, { scenario: 'cancel' });

    const assistant = new PlatformAiAssistantPage(page);
    await assistant.goto(authUrls.expert);
    await assistant.open();
    await assistant.startNewSession();
    await assistant.sendPrompt('请自动创建销售域建模方案');
    await assistant.expectApprovalCard();
    await assistant.cancel();
    await assistant.expectApprovalClosed();
    await checkpoints.mark('ai-modeling:canceled');

    const modelingPage = new ModelingPage(page);
    await modelingPage.goto(authUrls.expert);
    await modelingPage.expectNoGeneratedModels();
    await checkpoints.mark('ai-modeling:no-models');
  });
});
