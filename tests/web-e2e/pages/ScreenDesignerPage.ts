import { expect, type Locator, type Page } from '@playwright/test';

export class ScreenDesignerPage {
  readonly page: Page;
  readonly root: Locator;
  readonly primaryActionSelect: Locator;
  readonly primaryActionButton: Locator;
  readonly publishNotice: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('analytics-screen-designer');
    this.primaryActionSelect = page.getByTestId('analytics-screen-primary-action-select');
    this.primaryActionButton = page.getByTestId('analytics-screen-primary-action-button');
    this.publishNotice = page.getByTestId('analytics-screen-publish-notice');
  }

  async gotoEdit(analyticsBaseUrl: string, screenId: string): Promise<void> {
    await this.page.goto(analyticsBaseUrl, { waitUntil: 'domcontentloaded' });
    await this.page.locator('a[href="/analytics/screens"]').first().click();
    await this.page.waitForURL('**/analytics/screens');
    await this.page.getByTestId(`analytics-screen-edit-${screenId}`).click();
    await expect(this.root).toBeVisible();
  }

  async publish(): Promise<void> {
    this.page.once('dialog', async (dialog) => {
      await dialog.dismiss();
    });
    await this.primaryActionSelect.selectOption('publish');
    await this.primaryActionButton.click();
  }

  async expectPublished(versionText: string): Promise<void> {
    await expect(this.publishNotice).toBeVisible();
    await expect(this.publishNotice).toContainText(versionText);
  }
}
