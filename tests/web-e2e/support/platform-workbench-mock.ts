import type { Page, Route } from '@playwright/test';

type Favorite = {
  id: string;
  title: string;
  targetType?: string | null;
  targetId?: string | null;
  link?: string | null;
  sortOrder?: number | null;
  enabled?: boolean;
};

type TodoItem = {
  type: string;
  title: string;
  status: string;
  createdAt: string;
  taskId?: string;
  requestId?: string;
  datasetId?: string;
  message?: string;
};

function ok(data: unknown) {
  return {
    status: 'SUCCESS',
    data,
  };
}

function json(route: Route, data: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: 'application/json; charset=utf-8',
    body: JSON.stringify(data),
  });
}

async function readJsonBody(route: Route): Promise<Record<string, unknown>> {
  const raw = route.request().postData() || '{}';
  return JSON.parse(raw) as Record<string, unknown>;
}

function nowIso(): string {
  return new Date().toISOString();
}

export async function installPlatformWorkbenchMocks(page: Page): Promise<void> {
  const todos: TodoItem[] = [
    {
      type: 'ACCESS_APPROVAL',
      title: '销售数据集访问申请',
      status: '待审批',
      createdAt: '2026-03-09T09:00:00Z',
      requestId: 'REQ-1001',
      message: '需要确认华东销售域访问范围',
    },
    {
      type: 'QUALITY',
      title: '客户主数据质量复核',
      status: '待处理',
      createdAt: '2026-03-09T08:10:00Z',
      taskId: 'TASK-2001',
      message: '客户名称缺失率高于阈值',
    },
  ];

  const favorites: Favorite[] = [
    {
      id: 'fav-sales-dataset',
      title: '销售主题数据集',
      targetType: 'DATASET',
      targetId: 'sales_dataset',
      link: '/dashboard/catalog/datasets',
      sortOrder: 1,
      enabled: true,
    },
  ];

  await page.route('**/api/menu/tree**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/app-packs', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/app-packs/pages', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/workbench/overview', async (route) => {
    await json(
      route,
      ok({
        generatedAt: nowIso(),
        myAssets: 12,
        todayNewAssets: 3,
      }),
    );
  });

  await page.route('**/api/workbench/todos', async (route) => {
    await json(route, ok(todos));
  });

  await page.route('**/api/workbench/favorites', async (route) => {
    if (route.request().method() === 'POST') {
      const payload = await readJsonBody(route);
      const next: Favorite = {
        id: `fav-${favorites.length + 1}`,
        title: String(payload.title || '未命名收藏'),
        targetType: String(payload.targetType || 'LINK'),
        targetId: payload.targetId ? String(payload.targetId) : null,
        link: payload.link ? String(payload.link) : null,
        sortOrder: payload.sortOrder ? Number(payload.sortOrder) : favorites.length + 1,
        enabled: payload.enabled !== false,
      };
      favorites.unshift(next);
      await json(route, ok(next));
      return;
    }
    await json(route, ok(favorites));
  });

  await page.route('**/api/workbench/favorites/*', async (route) => {
    const favoriteId = route.request().url().split('/').filter(Boolean).at(-1) || '';
    if (route.request().method() === 'PUT') {
      const payload = await readJsonBody(route);
      const favorite = favorites.find((item) => item.id === favoriteId);
      if (!favorite) {
        await json(route, { message: 'not found' }, 404);
        return;
      }
      favorite.title = String(payload.title || favorite.title);
      favorite.targetType = String(payload.targetType || favorite.targetType || 'LINK');
      favorite.targetId = payload.targetId ? String(payload.targetId) : null;
      favorite.link = payload.link ? String(payload.link) : null;
      favorite.sortOrder = payload.sortOrder ? Number(payload.sortOrder) : favorite.sortOrder;
      favorite.enabled = payload.enabled !== false;
      await json(route, ok(favorite));
      return;
    }
    await json(route, ok(true));
  });
}
