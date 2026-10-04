import { expect, type Page } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage';

export interface LoginToProtectedRouteOptions {
  targetUrl: string;
  username: string;
  password: string;
  expectedReturnPath: string;
}

function escapeForRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export async function loginToProtectedRoute(
  page: Page,
  options: LoginToProtectedRouteOptions,
): Promise<void> {
  await page.goto(options.targetUrl, { waitUntil: 'domcontentloaded' });

  const loginPage = new LoginPage(page);
  await loginPage.expectLoaded(options.expectedReturnPath);
  await loginPage.login(options.username, options.password);

  await expect(page).toHaveURL(new RegExp(`${escapeForRegex(options.expectedReturnPath)}$`));
}
