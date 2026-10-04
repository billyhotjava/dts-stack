import { test, expect, requireShellTarget } from '../../fixtures/base.fixture';

test('app shell should open configured target and expose document shell', async ({ page }) => {
  const target = requireShellTarget();

  await page.goto(target.url, { waitUntil: 'domcontentloaded' });

  await expect(page).toHaveURL(target.url);
  await expect(page.locator('body')).toBeVisible();
});
