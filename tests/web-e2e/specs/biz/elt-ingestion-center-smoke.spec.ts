import { test, expect, storageStatePathFor } from '../../fixtures/reset.fixture';
import { IngestionPage } from '../../pages/IngestionPage';
import { TransformDetailPage } from '../../pages/TransformDetailPage';
import { installPlatformIngestionCenterMocks } from '../../support/platform-elt-smoke-mock';

test.describe('elt ingestion center smoke', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should execute an ingestion task and expose the latest execution log', async ({
    page,
    authUrls,
    checkpoints,
  }) => {
    await installPlatformIngestionCenterMocks(page);

    const ingestionPage = new IngestionPage(page);
    await ingestionPage.goto(authUrls.expert);
    await ingestionPage.expectTaskVisible('ERP 销售订单入湖');
    await checkpoints.mark('elt-ingestion:task-list-visible');

    await ingestionPage.triggerTaskExecution('ERP 销售订单入湖');
    await ingestionPage.expectExecutionSuccess();
    await checkpoints.mark('elt-ingestion:task-executed');

    await ingestionPage.openTask('ERP 销售订单入湖');

    const detailPage = new TransformDetailPage(page);
    await detailPage.expectLoaded('ERP 销售订单入湖');
    await detailPage.openLatestLog();
    await detailPage.expectLogContains('写入目标表 ods_erp_orders 完成');
    await checkpoints.mark('elt-ingestion:latest-log-visible');

    await expect(page).toHaveURL(/(?:#\/|\/)explore\/etl\/transform\/101$/);
  });
});
