import { expect, type Locator, type Page } from '@playwright/test';

export class PlatformAiAssistantPage {
  readonly page: Page;
  readonly root: Locator;
  readonly refreshButton: Locator;
  readonly todoPanel: Locator;
  readonly newFavoriteButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('platform-workbench-page');
    this.refreshButton = page.getByTestId('platform-workbench-refresh');
    this.todoPanel = page.getByTestId('platform-workbench-todos');
    this.newFavoriteButton = page.getByTestId('platform-workbench-new-favorite');
  }

  async goto(expertBaseUrl: string): Promise<void> {
    const targetUrl = new URL('dashboard/workbench', expertBaseUrl).toString();
    await this.page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
    await expect(this.root).toBeVisible();
    await expect(this.page.getByText('工作台总览')).toBeVisible();
  }

  favoriteCard(title: string): Locator {
    return this.page.locator('[data-testid^="platform-workbench-favorite-card-"]').filter({ hasText: title }).first();
  }

  async refresh(): Promise<void> {
    await this.refreshButton.click();
    await expect(this.root).toBeVisible();
  }

  async expectTodo(text: string): Promise<void> {
    await expect(this.todoPanel).toContainText(text);
  }

  async openCreateFavorite(): Promise<void> {
    await this.newFavoriteButton.click();
    await expect(this.page.getByRole('dialog')).toBeVisible();
  }

  async fillFavoriteForm(params: {
    title: string;
    type?: string;
    targetId?: string;
    link?: string;
    sortOrder?: string;
  }): Promise<void> {
    const dialog = this.page.getByRole('dialog');
    await dialog.getByLabel('收藏名称').fill(params.title);
    if (params.type) {
      await dialog.getByLabel('类型').click();
      await this.page.getByRole('option', { name: params.type }).click();
    }
    if (params.targetId) {
      await dialog.getByLabel('目标ID').fill(params.targetId);
    }
    if (params.link) {
      await dialog.getByLabel('跳转链接').fill(params.link);
    }
    if (params.sortOrder) {
      await dialog.getByLabel('排序').fill(params.sortOrder);
    }
  }

  async saveFavorite(): Promise<void> {
    const dialog = this.page.getByRole('dialog');
    await this.page.locator('.ant-modal-content button.ant-btn-primary').last().click();
    await expect(dialog).toHaveCount(0);
  }

  async expectFavoriteVisible(title: string): Promise<void> {
    await expect(this.favoriteCard(title)).toBeVisible();
  }

  async openFavorite(title: string): Promise<void> {
    const card = this.favoriteCard(title);
    await card.getByRole('button', { name: '打开' }).click();
  }

  async editFavorite(title: string): Promise<void> {
    const card = this.favoriteCard(title);
    await card.getByRole('button', { name: '编辑' }).click();
    await expect(this.page.getByRole('dialog')).toBeVisible();
  }

  async openDatasetsLink(): Promise<void> {
    await this.page.getByTestId('platform-workbench-link-datasets').click();
  }

  async openJobsLink(): Promise<void> {
    await this.page.getByTestId('platform-workbench-link-jobs').click();
  }

  async openDbtLink(): Promise<void> {
    await this.page.getByTestId('platform-workbench-link-dbt').click();
  }
}
