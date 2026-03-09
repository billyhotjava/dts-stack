import { expect, type Locator, type Page } from '@playwright/test';

export class AnalyticsScreenPage {
  readonly page: Page;
  readonly listRoot: Locator;
  readonly createButton: Locator;
  readonly templateGallery: Locator;
  readonly previewRoot: Locator;
  readonly runtimePanel: Locator;

  constructor(page: Page) {
    this.page = page;
    this.listRoot = page.getByTestId('analytics-screens-page');
    this.createButton = page.getByTestId('analytics-screen-create');
    this.templateGallery = page.getByTestId('analytics-screen-template-gallery');
    this.previewRoot = page.getByTestId('analytics-screen-preview');
    this.runtimePanel = page.getByTestId('analytics-screen-runtime-panel');
  }

  async gotoScreens(analyticsBaseUrl: string): Promise<void> {
    await this.page.goto(analyticsBaseUrl, { waitUntil: 'domcontentloaded' });
    await this.page.locator('a[href="/analytics/screens"]').first().click();
    await this.page.waitForURL('**/analytics/screens');
    await expect(this.listRoot).toBeVisible();
  }

  async openTemplateGallery(): Promise<void> {
    await this.createButton.click();
    await expect(this.templateGallery).toBeVisible();
  }

  async expectBuiltinTemplate(templateId: string, title: string): Promise<void> {
    await expect(this.page.getByTestId(`analytics-screen-template-builtin-${templateId}`)).toContainText(title);
  }

  async useBuiltinTemplate(templateId: string): Promise<void> {
    await this.page.getByTestId(`analytics-screen-template-builtin-${templateId}`).click();
    await this.page.getByTestId('analytics-screen-template-confirm').click();
  }

  currentScreenId(): string {
    const match = this.page.url().match(/\/screens\/([^/]+)\/edit/);
    if (!match?.[1]) {
      throw new Error(`Cannot resolve current screen id from URL: ${this.page.url()}`);
    }
    return match[1];
  }

  async gotoPreview(screenId: string): Promise<void> {
    await this.page.evaluate((currentScreenId) => {
      window.history.pushState({}, '', `/analytics/screens/${currentScreenId}/preview`);
      window.dispatchEvent(new PopStateEvent('popstate'));
    }, screenId);
    await this.page.waitForURL(`**/analytics/screens/${screenId}/preview`);
    await expect(this.previewRoot).toBeVisible();
  }

  componentByName(name: string): Locator {
    return this.page.locator(`[data-component-name="${name}"]`);
  }
}
