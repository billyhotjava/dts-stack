import { expect, type Locator, type Page } from '@playwright/test';
import { buildPlatformRouteUrl } from '../support/platform-route';

export class IngestionPage {
  readonly page: Page;
  readonly root: Locator;
  readonly refreshButton: Locator;
  readonly createButton: Locator;
  readonly taskTable: Locator;
  readonly progress: Locator;
  readonly progressDialog: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('platform-transform-page');
    this.refreshButton = page.getByTestId('platform-transform-refresh');
    this.createButton = page.getByTestId('platform-transform-create');
    this.taskTable = page.getByTestId('platform-transform-table');
    this.progress = page.getByTestId('platform-transform-progress');
    this.progressDialog = page.getByRole('dialog', { name: /执行进度：/ });
  }

  async goto(expertBaseUrl: string): Promise<void> {
    const targetUrl = buildPlatformRouteUrl(expertBaseUrl, '/explore/etl/transform');
    await this.page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
    await expect(this.root).toBeVisible();
    await expect(this.root).toContainText('入湖任务中心');
  }

  taskRow(name: string): Locator {
    return this.page.locator('.ant-table-row').filter({ hasText: name }).first();
  }

  async expectTaskVisible(name: string): Promise<void> {
    await expect(this.taskTable).toContainText(name);
  }

  async triggerTaskExecution(name: string): Promise<void> {
    const row = this.taskRow(name);
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: '执行' }).click();
    await this.page.locator('.ant-modal-confirm .ant-btn-primary').click();
  }

  async expectExecutionSuccess(): Promise<void> {
    await expect(this.progress).toContainText('执行成功');
  }

  async openTask(name: string): Promise<void> {
    if (await this.progressDialog.isVisible().catch(() => false)) {
      await this.progressDialog.getByRole('button', { name: '关 闭' }).click();
      await expect(this.progressDialog).toHaveCount(0);
    }
    await this.taskRow(name).getByText(name, { exact: true }).click();
  }
}
