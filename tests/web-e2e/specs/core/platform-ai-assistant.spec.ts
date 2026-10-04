import { test, storageStatePathFor } from '../../fixtures/auth.fixture';
import { PlatformAiAssistantPage } from '../../pages/PlatformAiAssistantPage';
import { installPlatformWorkbenchMocks } from '../../support/platform-workbench-mock';

test.describe('platform ai assistant', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should render workbench metrics and todo preview', async ({ page, authUrls }) => {
    await installPlatformWorkbenchMocks(page);

    const assistant = new PlatformAiAssistantPage(page);
    await assistant.goto(authUrls.expert);
    await assistant.expectTodo('销售数据集访问申请');
    await assistant.expectFavoriteVisible('销售主题数据集');
    await assistant.refresh();
  });

  test('should create and update personal favorite from workbench', async ({ page, authUrls }) => {
    await installPlatformWorkbenchMocks(page);

    const assistant = new PlatformAiAssistantPage(page);
    await assistant.goto(authUrls.expert);
    await assistant.openCreateFavorite();
    await assistant.fillFavoriteForm({
      title: '经营主题看板',
      targetId: 'dashboard-77',
      link: '/dashboard/workbench',
      sortOrder: '2',
    });
    await assistant.saveFavorite();
    await assistant.expectFavoriteVisible('经营主题看板');

    await assistant.editFavorite('经营主题看板');
    await assistant.fillFavoriteForm({
      title: '经营主题看板-更新',
      targetId: 'dashboard-77',
      link: '/dashboard/workbench',
      sortOrder: '2',
    });
    await assistant.saveFavorite();
    await assistant.expectFavoriteVisible('经营主题看板-更新');
  });
});
