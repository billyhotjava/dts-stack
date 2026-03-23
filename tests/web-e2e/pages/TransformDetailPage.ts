import { expect, type Locator, type Page } from '@playwright/test';

export class TransformDetailPage {
  readonly page: Page;
  readonly root: Locator;
  readonly openLogButton: Locator;
  readonly executeButton: Locator;
  readonly logDrawer: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('platform-transform-detail-page');
    this.openLogButton = page.getByTestId('platform-transform-open-log');
    this.executeButton = page.getByTestId('platform-transform-execute');
    this.logDrawer = page.getByTestId('platform-transform-log-drawer');
  }

  async expectLoaded(taskName: string): Promise<void> {
    await expect(this.root).toBeVisible();
    await expect(this.root).toContainText(taskName);
  }

  async openLatestLog(): Promise<void> {
    await this.openLogButton.click();
    await expect(this.logDrawer).toBeVisible();
  }

  async expectLogContains(text: string): Promise<void> {
    await expect(this.logDrawer).toContainText(text);
  }
}
