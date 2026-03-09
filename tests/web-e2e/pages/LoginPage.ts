import { expect, type Locator, type Page } from '@playwright/test';

export class LoginPage {
  readonly page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;
  readonly errorBox: Locator;
  readonly pageTitle: Locator;
  readonly returnUrlMeta: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByTestId('username');
    this.passwordInput = page.getByTestId('password');
    this.submitButton = page.getByTestId('login-submit');
    this.errorBox = page.getByTestId('login-error');
    this.pageTitle = page.getByRole('heading', { name: '统一登录' });
    this.returnUrlMeta = page.locator('.meta');
  }

  async expectLoaded(expectedReturnPath?: string): Promise<void> {
    await expect(this.page).toHaveURL(/\/auth\/login\?returnUrl=/);
    await expect(this.pageTitle).toBeVisible();
    await expect(this.usernameInput).toBeVisible();
    await expect(this.passwordInput).toBeVisible();
    await expect(this.submitButton).toBeVisible();
    if (expectedReturnPath) {
      await expect(this.returnUrlMeta).toContainText(expectedReturnPath);
    }
  }

  async login(username: string, password: string): Promise<void> {
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.submitButton.click();
  }
}
