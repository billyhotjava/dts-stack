import { expect, type Locator, type Page } from '@playwright/test';

export class GovernancePage {
  readonly page: Page;
  readonly root: Locator;
  readonly governanceNavButton: Locator;
  readonly qualityNavLink: Locator;
  readonly datasetSelect: Locator;
  readonly profileButton: Locator;
  readonly profileAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('platform-governance-page');
    this.governanceNavButton = page.getByRole('button', { name: '数据治理中心' });
    this.qualityNavLink = page.getByRole('link', { name: '质量报告' });
    this.datasetSelect = page.getByTestId('platform-governance-dataset-select');
    this.profileButton = page.getByTestId('platform-governance-profile');
    this.profileAlert = page.getByTestId('platform-governance-profile-alert');
  }

  async goto(expertBaseUrl: string): Promise<void> {
    await this.page.goto(expertBaseUrl, { waitUntil: 'domcontentloaded' });
    await expect(this.governanceNavButton).toBeVisible();
    await this.governanceNavButton.click();
    await Promise.all([
      this.page.waitForURL('**/governance/quality'),
      this.page.waitForResponse(response => response.url().includes('/api/catalog/datasets') && response.ok()),
      this.qualityNavLink.click(),
    ]);
    await expect(this.root).toBeVisible();
  }

  anomalyRow(id: string): Locator {
    return this.page.getByTestId(`platform-governance-anomaly-row-${id}`);
  }

  async selectDataset(name: string): Promise<void> {
    await this.datasetSelect.click();
    await expect(this.page.getByRole('option', { name })).toHaveCount(1);
    await this.page.keyboard.press('ArrowDown');
    await this.page.keyboard.press('Enter');
  }

  async startProfile(): Promise<void> {
    await this.profileButton.click();
  }

  async expectProfileAlert(text: string): Promise<void> {
    await expect(this.profileAlert).toContainText(text);
  }

  async expectAnomalySeverity(id: string, severity: string): Promise<void> {
    await expect(this.anomalyRow(id)).toContainText(severity);
  }

  async remediateAnomaly(id: string): Promise<void> {
    await this.anomalyRow(id).getByTestId('platform-governance-remediate').click();
  }

  async expectAnomalyStatus(id: string, status: string): Promise<void> {
    await expect(this.anomalyRow(id).getByTestId('platform-governance-status')).toContainText(status);
  }
}
