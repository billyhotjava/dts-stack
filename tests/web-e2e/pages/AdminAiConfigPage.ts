import { expect, type Locator, type Page } from '@playwright/test';

type InfraServiceKey = 'platform' | 'addax' | 'airflow' | 'openmetadata' | 'dbt';

export class AdminAiConfigPage {
  readonly page: Page;
  readonly root: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('admin-infra-settings-page');
  }

  async goto(adminBaseUrl: string): Promise<void> {
    const targetUrl = new URL('infra-settings', adminBaseUrl).toString();
    await this.page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
    await this.expectReady();
  }

  async expectReady(): Promise<void> {
    await expect(this.root).toBeVisible();
    await expect(this.panel('platform')).toBeVisible();
  }

  panel(service: InfraServiceKey): Locator {
    return this.page.getByTestId(`admin-infra-service-panel-${service}`);
  }

  async switchTo(service: InfraServiceKey): Promise<void> {
    await this.page.getByTestId(`admin-infra-switch-${service}`).click();
    await expect(this.panel(service)).toBeVisible();
  }

  async expectFieldValue(service: InfraServiceKey, label: string, value: string): Promise<void> {
    await expect(this.panel(service).getByLabel(label)).toHaveValue(value);
  }

  async fillField(service: InfraServiceKey, label: string, value: string): Promise<void> {
    await this.panel(service).getByLabel(label).fill(value);
  }

  async save(service: InfraServiceKey): Promise<void> {
    await this.page.getByTestId(`admin-infra-service-save-${service}`).click();
  }

  async testConnection(service: InfraServiceKey): Promise<void> {
    await this.page.getByTestId(`admin-infra-service-test-${service}`).click();
  }

  async expectToast(text: string): Promise<void> {
    await expect(this.page.locator('[data-sonner-toast]').filter({ hasText: text }).last()).toBeVisible();
  }

  async expectTestResult(service: InfraServiceKey, text: string): Promise<void> {
    await expect(this.page.getByTestId(`admin-infra-service-test-result-${service}`)).toContainText(text);
  }
}
