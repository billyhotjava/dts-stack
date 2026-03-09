import { expect, type Locator, type Page } from '@playwright/test';

export class AnalyticsQueryPage {
  readonly page: Page;
  readonly dashboardsRoot: Locator;
  readonly detailRoot: Locator;
  readonly shareButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.dashboardsRoot = page.getByTestId('analytics-dashboards-page');
    this.detailRoot = page.getByTestId('analytics-dashboard-detail');
    this.shareButton = page.getByTestId('analytics-dashboard-share');
  }

  async gotoDashboards(analyticsBaseUrl: string): Promise<void> {
    const targetUrl = new URL('dashboards', analyticsBaseUrl).toString();
    await this.page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
    await expect(this.dashboardsRoot).toBeVisible();
  }

  async expectDashboardVisible(id: string, title: string): Promise<void> {
    await expect(this.page.getByTestId(`analytics-dashboard-card-${id}`)).toContainText(title);
  }

  async openDashboard(id: string): Promise<void> {
    await this.page.getByTestId(`analytics-dashboard-card-${id}`).click();
    await expect(this.detailRoot).toBeVisible();
  }

  async expectDashboardDetail(title: string): Promise<void> {
    await expect(this.detailRoot).toContainText(title);
  }

  async createShareLink(): Promise<void> {
    await this.shareButton.click();
  }

  async expectShareLink(): Promise<void> {
    await expect(this.detailRoot.getByRole('textbox')).toHaveValue(/\/analytics\/public\/dashboard\//);
  }
}
