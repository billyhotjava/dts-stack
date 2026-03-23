import { test, expect, storageStatePathFor } from '../../fixtures/reset.fixture';
import { ModelingPage } from '../../pages/ModelingPage';
import { installPlatformSqlModelingMocks } from '../../support/platform-elt-smoke-mock';

test.describe('elt development center smoke', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should compile, test, and release the active dbt model group', async ({
    page,
    authUrls,
    checkpoints,
  }) => {
    await installPlatformSqlModelingMocks(page);

    const modelingPage = new ModelingPage(page);
    await modelingPage.goto(authUrls.expert);
    await modelingPage.expectActiveModel('biz_ads_delay_reason_trend');
    await checkpoints.mark('elt-modeling:workspace-visible');

    await modelingPage.triggerCompile();
    await expect(page.getByText('总计 21，成功 21，失败 0')).toBeVisible();
    await checkpoints.mark('elt-modeling:compile-success');

    await modelingPage.triggerTest();
    await expect(page.getByText('not_null_biz_ads_delay_reason_trend_delay_reason_category')).toBeVisible();
    await checkpoints.mark('elt-modeling:test-success');

    await modelingPage.openRelease();
    await modelingPage.submitRelease();
    await expect(page.getByText('dbt build 已提交')).toBeVisible();
    await checkpoints.mark('elt-modeling:build-submitted');

    await page.getByText('操作记录').click();
    await page.getByText('运行记录').click();
    await expect(page.getByText('+tag:project-management')).toBeVisible();
    await expect(page.getByRole('cell', { name: 'BUILD', exact: true })).toBeVisible();
    await checkpoints.mark('elt-modeling:run-record-visible');
  });
});
