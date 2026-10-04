import type { Page, Route } from '@playwright/test';

type Principal = 'opadmin' | 'viewer';

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

function principalFromRoute(route: Route): Principal {
  const auth = String(route.request().headers().authorization || '').toLowerCase();
  return auth.includes('opadmin') ? 'opadmin' : 'viewer';
}

function overviewFor(principal: Principal) {
  if (principal === 'opadmin') {
    return {
      generatedAt: '2026-03-09T10:00:00Z',
      myAssets: 26,
      todayNewAssets: 4,
    };
  }

  return {
    generatedAt: '2026-03-09T10:00:00Z',
    myAssets: 6,
    todayNewAssets: 1,
  };
}

function todosFor(principal: Principal) {
  if (principal === 'opadmin') {
    return [
      {
        type: 'ACCESS_APPROVAL',
        title: '高风险补数审批',
        status: '待审批',
        createdAt: '2026-03-09T09:10:00Z',
        taskId: 'TASK-RBAC-001',
        message: '涉及跨部门销售回补，需运维管理员确认。',
      },
      {
        type: 'QUALITY',
        title: '权限策略变更复核',
        status: '待处理',
        createdAt: '2026-03-09T08:30:00Z',
        taskId: 'TASK-RBAC-002',
        message: '角色权限清单已调整，请确认审批链路。',
      },
    ];
  }

  return [
    {
      type: 'QUALITY',
      title: '仅可查看个人待办',
      status: '只读',
      createdAt: '2026-03-09T08:50:00Z',
      taskId: 'TASK-RBAC-READONLY',
      message: '当前账号仅具备查看权限，不可发起审批。',
    },
  ];
}

function favoritesFor(principal: Principal) {
  if (principal === 'opadmin') {
    return [
      {
        id: 'fav-rbac-opadmin',
        title: '审批工作台',
        targetType: 'TASK',
        targetId: 'workflow-center',
        link: '/dashboard/workbench/workflow-center',
        sortOrder: 1,
        enabled: true,
      },
    ];
  }

  return [
    {
      id: 'fav-rbac-viewer',
      title: '个人工作台',
      targetType: 'LINK',
      targetId: 'workbench-view',
      link: '/dashboard/workbench',
      sortOrder: 1,
      enabled: true,
    },
  ];
}

export async function installPlatformAuthRbacHitlMocks(page: Page): Promise<void> {
  await page.route('**/api/menu/tree**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/menu', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/app-packs/pages**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/app-packs**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/workbench/overview', async (route) => {
    await json(route, ok(overviewFor(principalFromRoute(route))));
  });

  await page.route('**/api/workbench/todos', async (route) => {
    await json(route, ok(todosFor(principalFromRoute(route))));
  });

  await page.route('**/api/workbench/favorites', async (route) => {
    await json(route, ok(favoritesFor(principalFromRoute(route))));
  });
}
