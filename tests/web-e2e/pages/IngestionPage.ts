import { expect, type Locator, type Page } from '@playwright/test';

export class IngestionPage {
  readonly page: Page;
  readonly root: Locator;
  readonly catalogNavButton: Locator;
  readonly metadataNavLink: Locator;
  readonly pipelineSelect: Locator;
  readonly triggerButton: Locator;
  readonly tableSelect: Locator;
  readonly columnsTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('platform-metadata-page');
    this.catalogNavButton = page.getByRole('button', { name: '资产目录' });
    this.metadataNavLink = page.getByRole('link', { name: '元数据采集' });
    this.pipelineSelect = page.getByTestId('platform-metadata-pipeline-select');
    this.triggerButton = page.getByTestId('platform-metadata-trigger');
    this.tableSelect = page.getByTestId('platform-metadata-table-select');
    this.columnsTable = page.getByTestId('platform-metadata-columns-table');
  }

  async goto(expertBaseUrl: string): Promise<void> {
    await this.page.goto(expertBaseUrl, { waitUntil: 'domcontentloaded' });
    await expect(this.catalogNavButton).toBeVisible();
    await this.catalogNavButton.click();
    await Promise.all([
      this.page.waitForURL('**/catalog/metadata'),
      this.metadataNavLink.click(),
    ]);
    await expect(this.root).toBeVisible();
  }

  async selectPipeline(label: string): Promise<void> {
    await expect(this.pipelineSelect).toContainText(label);
  }

  async triggerSync(): Promise<void> {
    await this.triggerButton.click();
    await expect(this.tableSelect).toBeVisible();
  }

  async expectDiscoveredTable(fqn: string): Promise<void> {
    await expect(this.tableSelect).toContainText(fqn);
  }

  async expectColumnVisible(columnName: string): Promise<void> {
    await expect(this.columnsTable).toContainText(columnName);
  }
}
