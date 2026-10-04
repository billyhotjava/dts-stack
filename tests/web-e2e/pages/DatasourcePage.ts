import { expect, type Locator, type Page } from '@playwright/test';

type JdbcDatasourceInput = {
  name: string;
  typeLabel: string;
  jdbcUrl: string;
  username: string;
  password: string;
};

function toTestId(value: string): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

export class DatasourcePage {
  readonly page: Page;
  readonly root: Locator;
  readonly foundationNavButton: Locator;
  readonly datasourceNavLink: Locator;
  readonly createButton: Locator;
  readonly nameInput: Locator;
  readonly typeSelect: Locator;
  readonly jdbcUrlInput: Locator;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly saveButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByTestId('platform-datasource-page');
    this.foundationNavButton = page.getByRole('button', { name: '基础设施' });
    this.datasourceNavLink = page.getByRole('link', { name: '数据源连接' });
    this.createButton = page.getByTestId('platform-datasource-create');
    this.nameInput = page.getByTestId('platform-datasource-name');
    this.typeSelect = page.getByTestId('platform-datasource-type');
    this.jdbcUrlInput = page.getByTestId('platform-datasource-jdbc-url');
    this.usernameInput = page.getByTestId('platform-datasource-username');
    this.passwordInput = page.getByTestId('platform-datasource-password');
    this.saveButton = page.getByTestId('platform-datasource-save');
  }

  async goto(expertBaseUrl: string): Promise<void> {
    await this.page.goto(expertBaseUrl, { waitUntil: 'domcontentloaded' });
    await expect(this.foundationNavButton).toBeVisible();
    await this.foundationNavButton.click();
    await Promise.all([
      this.page.waitForURL('**/foundation/data-sources'),
      this.datasourceNavLink.click(),
    ]);
    await expect(this.root).toBeVisible();
  }

  row(nameOrId: string): Locator {
    return this.page.getByTestId(`platform-datasource-row-${toTestId(nameOrId)}`);
  }

  async createJdbcDatasource(input: JdbcDatasourceInput): Promise<void> {
    await this.createButton.click();
    await expect(this.nameInput).toBeVisible();
    await this.nameInput.fill(input.name);
    await this.typeSelect.click();
    await this.page.keyboard.press('ArrowDown');
    await this.page.keyboard.press('ArrowDown');
    await this.page.keyboard.press('Enter');
    await this.jdbcUrlInput.fill(input.jdbcUrl);
    await this.usernameInput.fill(input.username);
    await this.passwordInput.fill(input.password);
    await this.saveButton.click();
  }

  async expectRowVisible(nameOrId: string): Promise<void> {
    await expect(this.row(nameOrId)).toBeVisible();
  }
}
