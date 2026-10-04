import { expect, test, type Page, type TestInfo } from '@playwright/test';

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`F11 测试前置缺失：${name}；不能将缺环境或账号的用例记为通过。`);
  return value;
}

function platformOrigin(): string {
  const url = new URL(required('F11_PLATFORM_URL'));
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) {
    throw new Error('F11_PLATFORM_URL 必须是无内嵌凭据的 HTTP(S) 测试地址。');
  }
  if (url.pathname !== '/' || url.search || url.hash) {
    throw new Error('F11_PLATFORM_URL 只填写门户源站；页面入口由用例控制。');
  }
  return url.origin;
}

async function loginThroughVisiblePage(page: Page, testInfo: TestInfo): Promise<void> {
  const origin = platformOrigin();
  const mode = required('F11_LOGIN_MODE');
  if (!['password', 'pki'].includes(mode)) throw new Error('F11_LOGIN_MODE 必须为 password 或 pki。');
  // Require fixture expectations before opening a login session or doing any action.
  required('F11_ALLOWED_MENU_LABEL');
  required('F11_ALLOWED_MENU_HREF');
  required('F11_ALLOWED_PAGE_TEXT');

  await page.goto(`${origin}/#/login`);
  if (mode === 'password') {
    const username = required('F11_USERNAME');
    const password = process.env.F11_PASSWORD;
    if (!password) throw new Error('F11 测试前置缺失：F11_PASSWORD；不使用隐式测试密码。');
    await page.locator('input[name="username"]').fill(username);
    await page.locator('input[name="password"]').fill(password);
    await page.locator('form button[type="submit"]').click();
  } else {
    await page.getByRole('button', { name: '证书登录', exact: true }).click();
    testInfo.annotations.push({
      type: 'PKI 人工交接',
      description: '由实际操作人完成原证书/PIN/介质交互；只消费原流程结果，未模拟或修改 PKI。此登录步骤不等于 ST-001 全部兼容断言已验收。',
    });
  }
  await expect(page.getByRole('navigation').first()).toBeVisible({ timeout: mode === 'pki' ? 120_000 : 30_000 });
  expect(new URL(page.url()).origin).toBe(origin);
  await expect(page).not.toHaveURL(/#\/login(?:[/?]|$)/);
}

async function openAllowedMenu(page: Page): Promise<void> {
  const navigation = page.getByRole('navigation').first();
  const label = required('F11_ALLOWED_MENU_LABEL');
  const href = required('F11_ALLOWED_MENU_HREF');
  if (!href.startsWith('/') && !href.startsWith('#/')) {
    throw new Error('F11_ALLOWED_MENU_HREF 必须是当前门户的相对路由。');
  }
  // The operator chooses a visible permitted leaf in the fixture. No hidden/forced clicks.
  const link = navigation.getByRole('link', { name: label, exact: true });
  await expect(link).toBeVisible();
  await expect(link).toHaveAttribute('href', href);
  await link.click();
  await expect(page.locator('main').first()).toContainText(required('F11_ALLOWED_PAGE_TEXT'));
}

test.beforeEach(async ({ browser }, testInfo) => {
  const actual = browser.version();
  testInfo.annotations.push({ type: 'Chrome 实际版本', description: actual });
  const expectedMajor = process.env.F11_EXPECTED_CHROME_MAJOR?.trim();
  if (expectedMajor) expect(actual.split('.')[0], '不能用新浏览器或 UA 模拟代替指定 Chrome 版本').toBe(expectedMajor);
});

test('F11-ST-002 部分：员工真实页面登录、允许菜单与建模深链接拒绝', async ({ page }, testInfo) => {
  testInfo.annotations.push({ type: '覆盖边界', description: '仅 U-EMP 子项；纯自定义角色、无菜单和接口抗绕过仍需独立证据。' });
  const expectedDenial = required('F11_MODEL_DENIAL_TEXT');
  await loginThroughVisiblePage(page, testInfo);
  await openAllowedMenu(page);
  await page.screenshot({ path: testInfo.outputPath('01-allowed-page.png') });

  await page.goto(`${platformOrigin()}/#/data-modeling/dimensions/workbench`);
  await expect(page.getByRole('navigation').first()).toBeVisible();
  await expect(page.locator('main').first()).toContainText(expectedDenial);
  await expect(page.locator('main').first().getByText('模型目录', { exact: true })).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('02-modeling-denied.png') });

  await openAllowedMenu(page);
  await page.screenshot({ path: testInfo.outputPath('03-return-to-allowed-page.png') });
});

test('F11-ST-020 部分：已授权页面在桌面和窄视口可读', async ({ page }, testInfo) => {
  testInfo.annotations.push({ type: '覆盖边界', description: '只验证夹具选定的允许页面；不代表全部管理页面、弹窗或所有浏览器版本通过。' });
  await loginThroughVisiblePage(page, testInfo);
  await openAllowedMenu(page);
  for (const viewport of [{ width: 1366, height: 768 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport);
    await expect(page.locator('main').first()).toContainText(required('F11_ALLOWED_PAGE_TEXT'));
    const dimensions = await page.evaluate(() => ({
      client: document.documentElement.clientWidth,
      scroll: document.documentElement.scrollWidth,
    }));
    expect(dimensions.scroll, '页面本身不能溢出；宽表格应在自身容器滚动').toBeLessThanOrEqual(dimensions.client + 1);
    await page.screenshot({ path: testInfo.outputPath(`viewport-${viewport.width}.png`), fullPage: true });
  }
});
