import { expect, type Locator, type Page } from '@playwright/test';

export class AdminPackManagementPage {
  readonly page: Page;
  readonly root: Locator;
  readonly table: Locator;
  readonly filter: Locator;
  readonly detailSheet: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('admin-pack-management-page');
    this.table = page.getByTestId('admin-pack-table');
    this.filter = page.getByTestId('admin-pack-type-filter');
    this.detailSheet = page.getByTestId('admin-pack-detail-sheet');
  }

  async goto(adminBaseUrl: string): Promise<void> {
    const targetUrl = new URL('packs', adminBaseUrl).toString();
    await this.page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
    await expect(this.root).toBeVisible();
  }

  row(packId: string): Locator {
    return this.table.locator('tr').filter({
      has: this.page.getByTestId(`admin-pack-detail-open-${packId}`),
    }).first();
  }

  async filterBy(label: '全部' | '应用包' | '增值服务'): Promise<void> {
    await this.filter.getByText(label, { exact: true }).click();
  }

  async expectPackVisible(packId: string): Promise<void> {
    await expect(this.page.getByTestId(`admin-pack-detail-open-${packId}`)).toBeVisible();
  }

  async expectPackHidden(packId: string): Promise<void> {
    await expect(this.row(packId)).toHaveCount(0);
  }

  async install(packId: string): Promise<void> {
    await this.page.getByTestId(`admin-pack-install-${packId}`).click();
  }

  async toggle(packId: string): Promise<void> {
    await this.page.getByTestId(`admin-pack-toggle-${packId}`).click();
  }

  async uninstall(packId: string): Promise<void> {
    await this.page.getByTestId(`admin-pack-uninstall-${packId}`).click();
  }

  async openDetail(packId: string): Promise<void> {
    await this.page.getByTestId(`admin-pack-detail-open-${packId}`).click();
    await expect(this.detailSheet).toBeVisible();
  }

  async expectDetailContains(text: string): Promise<void> {
    await expect(this.detailSheet).toContainText(text);
  }

  async expectRowStatus(packId: string, text: string): Promise<void> {
    await expect(this.row(packId)).toContainText(text);
  }

  async expectToast(text: string): Promise<void> {
    await expect(this.page.locator('[data-sonner-toast]').filter({ hasText: text }).last()).toBeVisible();
  }
}
