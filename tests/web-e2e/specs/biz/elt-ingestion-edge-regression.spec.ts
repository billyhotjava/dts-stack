import { test, expect, storageStatePathFor } from '../../fixtures/reset.fixture';
import { IngestionPage } from '../../pages/IngestionPage';
import { installPlatformIngestionEdgeMocks } from '../../support/platform-elt-smoke-mock';

test.describe('elt ingestion edge regression', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should support failed retry, dag rebuild, and execution history follow-up', async ({
    page,
    authUrls,
    checkpoints,
  }) => {
    await installPlatformIngestionEdgeMocks(page);

    const ingestionPage = new IngestionPage(page);
    await ingestionPage.goto(authUrls.expert);
    await ingestionPage.expectTaskVisible('ERP 销售订单入湖');
    await checkpoints.mark('elt-ingestion-edge:list-visible');

    const taskRow = ingestionPage.taskRow('ERP 销售订单入湖');
    await taskRow.getByRole('button', { name: '历史' }).click();
    await expect(page).toHaveURL(/(?:#\/|\/)explore\/etl\/transform\/101\/executions$/);
    await expect(page.getByText('ERP 销售订单入湖 - 执行历史')).toBeVisible();

    await expect(page.getByText('失败重试')).toBeVisible();
    await page.getByRole('button', { name: '失败重试' }).click();
    await expect(page.getByRole('row', { name: /ingestion-run-9002.*失败重试.*成功/ })).toBeVisible({ timeout: 20000 });
    const progressDialog = page.getByRole('dialog', { name: '执行进度' });
    await expect(progressDialog.getByText('执行成功')).toBeVisible({ timeout: 20000 });
    await progressDialog.getByRole('button', { name: /关\s*闭|最小化/ }).click();
    await checkpoints.mark('elt-ingestion-edge:retry-success');

    await page.getByRole('button', { name: '返回' }).click();
    await expect(page).toHaveURL(/(?:#\/|\/)explore\/etl\/transform\/101$/);
    await page.getByRole('button', { name: '重建 DAG' }).click();
    await page.locator('.ant-modal-confirm .ant-btn-primary').click();
    await expect(page.getByText('DAG 已重建')).toBeVisible();
    await checkpoints.mark('elt-ingestion-edge:rebuild-success');

    await page.getByRole('button', { name: '执行任务' }).click();
    const detailProgressDialog = page.getByRole('dialog', { name: '执行进度' });
    await expect(detailProgressDialog.getByText('执行成功')).toBeVisible({ timeout: 20000 });
    await detailProgressDialog.getByRole('button', { name: /关\s*闭|最小化/ }).click();
    await checkpoints.mark('elt-ingestion-edge:execute-success');
  });
});
