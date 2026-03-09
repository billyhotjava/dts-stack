import { test, expect, storageStatePathFor } from '../../fixtures/reset.fixture';
import { PlatformAiAssistantPage } from '../../pages/PlatformAiAssistantPage';
import { installPlatformWorkbenchMocks } from '../../support/platform-workbench-mock';

test.describe('erp ingest visible journey', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should surface ERP ingestion todo and keep ERP dataset shortcut visible from workbench', async ({
    page,
    authUrls,
    checkpoints,
  }) => {
    await installPlatformWorkbenchMocks(page, {
      overview: {
        myAssets: 20,
        todayNewAssets: 2,
      },
      todos: [
        {
          type: 'QUALITY',
          title: 'ERP 元数据同步完成',
          status: '待确认',
          createdAt: '2026-03-09T09:12:00Z',
          datasetId: 'erp-sales-orders',
          message: '发现 2 张 ERP 表，请确认销售订单主题资产是否可见。',
        },
      ],
      favorites: [
        {
          id: 'fav-erp-dataset',
          title: 'ERP 销售主题集',
          targetType: 'DATASET',
          targetId: 'erp-sales-orders',
          link: '/dashboard/catalog/datasets',
          sortOrder: 1,
          enabled: true,
        },
      ],
    });

    const workbench = new PlatformAiAssistantPage(page);
    await workbench.goto(authUrls.expert);
    await checkpoints.mark('erp-ingest:workbench-visible');
    await workbench.expectTodo('ERP 元数据同步完成');
    await workbench.expectFavoriteVisible('ERP 销售主题集');

    await workbench.openCreateFavorite();
    await workbench.fillFavoriteForm({
      title: 'ERP 订单主题资产',
      targetType: 'DATASET',
      targetId: 'erp-sales-orders',
      link: '/dashboard/catalog/datasets',
      sortOrder: '2',
    });
    await workbench.saveFavorite();
    await workbench.expectFavoriteVisible('ERP 订单主题资产');
    await checkpoints.mark('erp-ingest:favorite-created');

    await workbench.openFavorite('ERP 销售主题集');
    await expect(page).toHaveURL(/\/dashboard\/catalog\/datasets/);
    await checkpoints.mark('erp-ingest:dataset-link-opened');
  });
});
