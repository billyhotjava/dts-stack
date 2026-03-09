import { expect, type Locator, type Page } from '@playwright/test';

function toTestId(value: string): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

export class ModelingPage {
  readonly page: Page;
  readonly root: Locator;
  readonly modelingNavButton: Locator;
  readonly modelingNavLink: Locator;
  readonly latestBuildStatus: Locator;
  readonly activeModel: Locator;
  readonly syncStats: Locator;
  readonly emptyState: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('platform-modeling-page');
    this.modelingNavButton = page.getByRole('button', { name: '数仓建模' });
    this.modelingNavLink = page.getByRole('link', { name: '逻辑建模' });
    this.latestBuildStatus = page.getByTestId('platform-modeling-latest-build-status');
    this.activeModel = page.getByTestId('platform-modeling-active-model');
    this.syncStats = page.getByTestId('platform-modeling-sync-stats');
    this.emptyState = page.getByTestId('platform-modeling-empty');
  }

  async goto(expertBaseUrl: string): Promise<void> {
    await this.page.goto(expertBaseUrl, { waitUntil: 'domcontentloaded' });
    await expect(this.modelingNavButton).toBeVisible();
    await this.modelingNavButton.click();
    await Promise.all([
      this.page.waitForURL('**/modeling/sql'),
      this.modelingNavLink.click(),
    ]);
    await expect(this.root).toBeVisible();
  }

  modelNode(name: string): Locator {
    return this.page.getByTestId(`platform-modeling-model-${toTestId(name)}`);
  }

  async expectModelVisible(name: string): Promise<void> {
    await expect(this.modelNode(name)).toBeVisible();
  }

  async expectActiveModel(name: string): Promise<void> {
    await expect(this.activeModel).toContainText(name);
  }

  async expectSyncStats(text: string): Promise<void> {
    await expect(this.syncStats).toContainText(text);
  }

  async expectNoGeneratedModels(): Promise<void> {
    await expect(this.emptyState).toBeVisible();
  }

  async expectLatestBuildStatus(status: string): Promise<void> {
    await expect(this.latestBuildStatus).toContainText(status);
  }
}
