import type { Page, Route } from '@playwright/test';

type Principal = 'opadmin' | 'viewer';

type MockSession = {
  id: string;
  title: string;
  createdAt: string;
  lastActiveAt: string;
};

type PendingAction = {
  actionId: string;
  toolId: string;
  params: Record<string, unknown>;
  reason: string;
  riskLevel: 'HIGH';
};

type PrincipalState = {
  session: MockSession | null;
  prompt: string;
  pendingAction: PendingAction | null;
  assistantMessage: string;
  toolResult: string;
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

function unauthorized(route: Route, message = 'forbidden') {
  return json(route, { status: 'ERROR', message }, 403);
}

function principalFromRoute(route: Route): Principal {
  const auth = route.request().headers().authorization || '';
  if (auth.includes('opadmin')) {
    return 'opadmin';
  }
  return 'viewer';
}

function buildMenuTree(principal: Principal) {
  const shared = [
    {
      id: 'workbench-root',
      parentId: '0',
      name: '工作台',
      displayName: '工作台',
      code: 'workbench',
      type: 1,
      path: '/workbench',
      children: [
        {
          id: 'workbench-home',
          parentId: 'workbench-root',
          name: '我的概览',
          displayName: '我的概览',
          code: 'workbench:home',
          type: 2,
          path: '/workbench',
          component: '/pages/workbench',
          children: [],
        },
      ],
    },
  ];

  if (principal !== 'opadmin') {
    return shared;
  }

  return [
    ...shared,
    {
      id: 'catalog-root',
      parentId: '0',
      name: '资产目录',
      displayName: '资产目录',
      code: 'catalog',
      type: 1,
      path: '/catalog',
      children: [
        {
          id: 'catalog-metadata',
          parentId: 'catalog-root',
          name: '元数据采集',
          displayName: '元数据采集',
          code: 'catalog:metadata',
          type: 2,
          path: '/catalog/metadata',
          component: '/pages/catalog/MetadataPage',
          children: [],
        },
      ],
    },
    {
      id: 'foundation-root',
      parentId: '0',
      name: '基础设施',
      displayName: '基础设施',
      code: 'foundation',
      type: 1,
      path: '/foundation',
      children: [
        {
          id: 'foundation-data-sources',
          parentId: 'foundation-root',
          name: '数据源连接',
          displayName: '数据源连接',
          code: 'foundation:data-sources',
          type: 2,
          path: '/foundation/data-sources',
          component: '/pages/foundation/DataSourcesPage',
          children: [],
        },
      ],
    },
  ];
}

function buildInitialState(): PrincipalState {
  return {
    session: null,
    prompt: '',
    pendingAction: {
      actionId: 'rbac-hitl-1',
      toolId: 'trigger_pipeline',
      params: {
        dagId: 'sales-alert-dag',
        conf: { bizDate: '2026-03-07' },
      },
      reason: '需要确认高风险流水线触发动作',
      riskLevel: 'HIGH',
    },
    assistantMessage: '已识别异常销售波动，建议先进入审批再执行补数任务。',
    toolResult: JSON.stringify({
      success: true,
      textSummary: '高风险动作已确认并进入执行',
      errorMessage: null,
      data: { status: 'APPROVED', dagId: 'sales-alert-dag' },
    }),
  };
}

function buildSessionDetail(state: PrincipalState) {
  return {
    session: state.session,
    messages: state.session
      ? [
          {
            id: 'msg-user-1',
            sessionId: state.session.id,
            role: 'user',
            content: state.prompt,
            sequenceNum: 1,
            createdAt: state.session.createdAt,
          },
          {
            id: 'msg-tool-1',
            sessionId: state.session.id,
            role: 'tool',
            toolName: 'run_sql',
            toolParams: JSON.stringify({ sql: 'select * from sales_alerts' }),
            toolResult: state.toolResult,
            sequenceNum: 2,
            createdAt: state.session.createdAt,
          },
          {
            id: 'msg-assistant-1',
            sessionId: state.session.id,
            role: 'assistant',
            content: state.assistantMessage,
            sequenceNum: 3,
            createdAt: state.session.createdAt,
          },
        ]
      : [],
    pendingAction: state.pendingAction,
  };
}

function sseMessage(state: PrincipalState) {
  return [
    'event: token',
    'data: {"content":"已识别异常销售"}',
    '',
    'event: token',
    'data: {"content":"波动"}',
    '',
    'event: message',
    `data: ${JSON.stringify({
      sessionId: state.session?.id,
      content: state.assistantMessage,
      pendingAction: state.pendingAction,
    })}`,
    '',
    'event: done',
    'data: {}',
    '',
  ].join('\n');
}

async function readJsonBody(route: Route): Promise<Record<string, unknown>> {
  const raw = route.request().postData() || '{}';
  return JSON.parse(raw) as Record<string, unknown>;
}

function nowIso(): string {
  return new Date().toISOString();
}

export async function installPlatformAuthRbacHitlMocks(page: Page): Promise<void> {
  const states: Record<Principal, PrincipalState> = {
    opadmin: buildInitialState(),
    viewer: buildInitialState(),
  };

  await page.route('**/api/menu/tree', async (route) => {
    await json(route, ok(buildMenuTree(principalFromRoute(route))));
  });

  await page.route('**/api/menu', async (route) => {
    await json(route, ok(buildMenuTree(principalFromRoute(route))));
  });

  await page.route('**/api/app-packs/pages', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/app-packs', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/workbench/overview', async (route) => {
    const principal = principalFromRoute(route);
    await json(
      route,
      ok({
        totalDatasets: principal === 'opadmin' ? 128 : 24,
        publishedReports: principal === 'opadmin' ? 18 : 3,
        qualitySuccessRate: 0.96,
        opsSuccessRate: 0.91,
        aiCalls: principal === 'opadmin' ? 42 : 3,
      }),
    );
  });

  await page.route('**/api/workbench/trends?*', async (route) => {
    await json(
      route,
      ok({
        dates: ['03-01', '03-02', '03-03', '03-04', '03-05', '03-06', '03-07'],
        assetGrowth: [1, 2, 2, 3, 3, 4, 5],
        qualityRate: [0.93, 0.95, 0.96, 0.94, 0.97, 0.96, 0.98],
        opsRate: [0.88, 0.89, 0.9, 0.91, 0.92, 0.91, 0.93],
      }),
    );
  });

  await page.route('**/api/workbench/hot-items?*', async (route) => {
    await json(
      route,
      ok({
        hotAssets: [
          { id: 'asset-1', name: '销售订单', category: 'ERP', updatedAt: '2026-03-07T09:00:00Z' },
          { id: 'asset-2', name: '客户主数据', category: 'MDM', updatedAt: '2026-03-07T09:10:00Z' },
        ],
        hotReports: [
          { id: 'report-1', name: '销售日报', type: 'dashboard', visitCount: 13, url: '/analytics/dashboards/77' },
        ],
      }),
    );
  });

  await page.route('**/api/workbench/leader-overview?*', async (route) => {
    await json(
      route,
      ok({
        governance: {
          kpi: { qualitySuccessRate: 0.97 },
          quality: { success: 12, failed: 1 },
          compliance: { total: 8, completed: 7, failed: 1 },
          issue: { resolved: 5, overdue: 1 },
        },
        publishedReports: [
          { id: 'leader-report-1', title: '经营质量周报', reportType: 'dashboard', url: '/analytics/dashboards/77' },
        ],
        aiCalls: 42,
        aiTrend: [
          { date: '03-05', calls: 10, tokens: 1024 },
          { date: '03-06', calls: 15, tokens: 2048 },
          { date: '03-07', calls: 17, tokens: 3072 },
        ],
        totalDatasets: 128,
      }),
    );
  });

  await page.route('**/api/ai/agent/sessions?*', async (route) => {
    const principal = principalFromRoute(route);
    const state = states[principal];
    await json(route, ok(state.session ? [state.session] : []));
  });

  await page.route('**/api/ai/agent/sessions/*', async (route) => {
    const principal = principalFromRoute(route);
    const state = states[principal];
    if (route.request().method() === 'DELETE') {
      state.session = null;
      state.pendingAction = null;
      state.prompt = '';
      await json(route, ok(true));
      return;
    }
    await json(route, ok(buildSessionDetail(state)));
  });

  await page.route('**/api/ai/agent/chat/stream', async (route) => {
    const principal = principalFromRoute(route);
    if (principal !== 'opadmin') {
      await unauthorized(route, 'AI assistant is forbidden for current role');
      return;
    }

    const state = states[principal];
    const body = await readJsonBody(route);
    const prompt = String(body.userMessage || '').trim() || '请检查销售异常';
    const now = nowIso();
    state.prompt = prompt;
    state.session = {
      id: 'sess-rbac-1',
      title: prompt.slice(0, 12),
      createdAt: now,
      lastActiveAt: now,
    };

    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream; charset=utf-8',
      body: sseMessage(state),
      headers: {
        'cache-control': 'no-cache',
        connection: 'keep-alive',
      },
    });
  });

  await page.route('**/api/ai/agent/chat', async (route) => {
    const principal = principalFromRoute(route);
    if (principal !== 'opadmin') {
      await unauthorized(route, 'AI assistant is forbidden for current role');
      return;
    }
    const state = states[principal];
    await json(
      route,
      ok({
        sessionId: state.session?.id ?? 'sess-rbac-1',
        agentMessage: state.assistantMessage,
        toolCalls: [],
        requiresApproval: Boolean(state.pendingAction),
        pendingAction: state.pendingAction,
      }),
    );
  });

  await page.route('**/api/ai/agent/chat/approve', async (route) => {
    const principal = principalFromRoute(route);
    if (principal !== 'opadmin') {
      await unauthorized(route, 'approve forbidden');
      return;
    }
    const state = states[principal];
    state.pendingAction = null;
    state.assistantMessage = '已确认执行，异常销售补数任务已进入执行队列。';
    state.toolResult = JSON.stringify({
      success: true,
      textSummary: '已进入执行队列',
      errorMessage: null,
      data: { status: 'RUNNING', dagId: 'sales-alert-dag' },
    });
    await json(route, ok(true));
  });

  await page.route('**/api/ai/agent/chat/cancel', async (route) => {
    const principal = principalFromRoute(route);
    if (principal !== 'opadmin') {
      await unauthorized(route, 'cancel forbidden');
      return;
    }
    const state = states[principal];
    state.pendingAction = null;
    state.assistantMessage = '已取消该高风险动作。';
    await json(route, ok(true));
  });

  await page.route('**/api/ai/agent/feedback', async (route) => {
    await json(route, ok(true));
  });
}
