import { expect, type Locator, type Page } from '@playwright/test';

export class DashboardPage {
  readonly page: Page;
  readonly editorRoot: Locator;
  readonly nameInput: Locator;
  readonly cardSelect: Locator;
  readonly addCardButton: Locator;
  readonly saveButton: Locator;
  readonly detailRoot: Locator;

  constructor(page: Page) {
    this.page = page;
    this.editorRoot = page.getByTestId('analytics-dashboard-editor');
    this.nameInput = page.getByTestId('analytics-dashboard-name-input');
    this.cardSelect = page.getByTestId('analytics-dashboard-card-select');
    this.addCardButton = page.getByTestId('analytics-dashboard-add-card');
    this.saveButton = page.getByTestId('analytics-dashboard-save-button');
    this.detailRoot = page.getByTestId('analytics-dashboard-detail');
  }

  async gotoCreate(analyticsBaseUrl: string): Promise<void> {
    await this.page.goto(analyticsBaseUrl, { waitUntil: 'domcontentloaded' });
    await this.page.locator('a[href="/analytics/dashboards"]').first().click();
    await this.page.waitForURL('**/analytics/dashboards');
    await this.page.locator('a[href="/analytics/dashboards/new"]').first().click();
    await expect(this.editorRoot).toBeVisible();
  }

  async createWithCard(name: string, cardId: string): Promise<void> {
    await this.nameInput.fill(name);
    await this.cardSelect.selectOption(cardId);
    await this.addCardButton.click();
    await this.saveButton.click();
  }

  async expectSaved(dashboardId: string): Promise<void> {
    await this.page.waitForURL(`**/analytics/dashboards/${dashboardId}`);
    await expect(this.detailRoot).toBeVisible();
  }

  dashcard(dashcardId: string): Locator {
    return this.page.getByTestId(`analytics-dashboard-dashcard-${dashcardId}`);
  }
}
