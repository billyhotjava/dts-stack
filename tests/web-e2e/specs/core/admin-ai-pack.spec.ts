import { test, storageStatePathFor } from '../../fixtures/auth.fixture';
import { AdminAiConfigPage } from '../../pages/AdminAiConfigPage';
import { installAdminAppMocks } from '../../support/admin-app-mock';

test.describe('admin core management', () => {
  test.use({ storageState: storageStatePathFor('admin') });

  test('should load infra settings and switch service panels', async ({ page, authUrls }) => {
    await installAdminAppMocks(page);

    const aiConfigPage = new AdminAiConfigPage(page);
    await aiConfigPage.goto(authUrls.admin);
    await aiConfigPage.switchTo('dbt');
    await aiConfigPage.expectFieldValue('dbt', 'DBT 地址', 'http://dbt-service:8080');
    await aiConfigPage.switchTo('platform');
    await aiConfigPage.expectReady();
  });

  test('should save and test dbt integration settings', async ({ page, authUrls }) => {
    await installAdminAppMocks(page);

    const aiConfigPage = new AdminAiConfigPage(page);
    await aiConfigPage.goto(authUrls.admin);
    await aiConfigPage.switchTo('dbt');
    await aiConfigPage.fillField('dbt', 'DBT 地址', 'http://dbt-service:8081');
    await aiConfigPage.save('dbt');
    await aiConfigPage.expectToast('配置已保存');
    await aiConfigPage.testConnection('dbt');
    await aiConfigPage.expectTestResult('dbt', 'dbt 连接成功');
  });
});
