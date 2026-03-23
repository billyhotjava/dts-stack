import { expect, type Locator, type Page } from '@playwright/test';
import { buildPlatformRouteUrl } from '../support/platform-route';

export class ModelingPage {
  readonly page: Page;
  readonly root: Locator;
  readonly compileButton: Locator;
  readonly testButton: Locator;
  readonly releaseButton: Locator;
  readonly activeModel: Locator;
  readonly opsTabs: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('platform-sql-modeling-page');
    this.compileButton = page.getByTestId('platform-sql-modeling-compile');
    this.testButton = page.getByTestId('platform-sql-modeling-test');
    this.releaseButton = page.getByTestId('platform-sql-modeling-release');
    this.activeModel = page.getByTestId('platform-sql-modeling-active-model');
    this.opsTabs = page.getByTestId('platform-sql-modeling-ops-tabs');
  }

  async goto(expertBaseUrl: string): Promise<void> {
    const targetUrl = buildPlatformRouteUrl(expertBaseUrl, '/modeling/sql');
    await this.page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
    await expect(this.root).toBeVisible();
    await expect(this.page.getByText('逻辑建模工作区')).toBeVisible();
  }

  async expectActiveModel(name: string): Promise<void> {
    await expect(this.activeModel).toContainText(name);
  }

  async triggerCompile(): Promise<void> {
    await this.compileButton.click();
  }

  async triggerTest(): Promise<void> {
    await this.testButton.click();
  }

  async openRelease(): Promise<void> {
    await this.releaseButton.click();
    await expect(this.page.getByRole('dialog', { name: '上线 (dbt build)' })).toBeVisible();
  }

  async submitRelease(): Promise<void> {
    const dialog = this.page.getByRole('dialog', { name: '上线 (dbt build)' });
    await dialog.getByRole('button', { name: /提\s*交/ }).click();
  }

  async switchOperationTab(label: string): Promise<void> {
    await this.page.getByRole('tab', { name: label }).click();
  }
}
