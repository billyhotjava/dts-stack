import type { Page, Route } from '@playwright/test';

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
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
};

type MockState = {
  session: MockSession | null;
  pendingAction: PendingAction | null;
  prompt: string;
  assistantMessage: string;
  dashboard: {
    id: number;
    name: string;
    description: string;
    dashcards: Array<{
      id: number;
      card_id: number;
      row: number;
      col: number;
      size_x: number;
      size_y: number;
    }>;
    publicUuid: string | null;
  };
};

function envelope(data: unknown) {
  return {
    status: 200,
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

function nowIso(): string {
  return new Date().toISOString();
}

async function readJsonBody(route: Route): Promise<Record<string, unknown>> {
  const raw = route.request().postData() || '{}';
  return JSON.parse(raw) as Record<string, unknown>;
}

function buildCardDetail(cardId: string) {
  return {
    id: Number(cardId),
    name: '客户销售总览',
    description: 'AI 生成的客户销售分析卡片',
    display: 'table',
    visualization_settings: {},
    dataset_query: {},
  };
}

function buildCardList() {
  return [
    {
      id: 42,
      name: '客户销售总览',
      description: 'AI 生成的客户销售分析卡片',
      display: 'table',
    },
  ];
}

function buildCollectionList() {
  return [
    {
      id: 7,
      name: '测试集',
      description: 'Playwright dashboard fixtures',
      can_write: true,
    },
  ];
}

function buildCardQuery() {
  return {
    status: 'completed',
    row_count: 2,
    data: {
      cols: [
        { name: 'customer_id', display_name: 'customer_id', base_type: 'type/Integer' },
        { name: 'customer_name', display_name: 'customer_name', base_type: 'type/Text' },
        { name: 'total_amount', display_name: 'total_amount', base_type: 'type/Float' },
      ],
      rows: [
        [1001, '华东装备', 128000],
        [1002, '北方能源', 96000],
      ],
      native_form: {
        query: 'select customer_id, customer_name, total_amount from ads_customer_sales',
      },
    },
  };
}

function buildDashboardDetail(state: MockState) {
  return {
    id: state.dashboard.id,
    name: state.dashboard.name,
    description: state.dashboard.description,
    collection_id: 7,
    parameters: [],
    ordered_cards: state.dashboard.dashcards.map((dashcard) => ({
      ...dashcard,
      card: buildCardDetail(String(dashcard.card_id)),
    })),
  };
}

function buildDashboardList(state: MockState) {
  return [
    {
      id: state.dashboard.id,
      name: state.dashboard.name,
      description: state.dashboard.description,
      collection_id: 7,
      public_uuid: state.dashboard.publicUuid,
    },
  ];
}

function buildDraftScreen(screenId: string) {
  return {
    id: Number(screenId),
    name: '经营驾驶舱',
    description: '发布前草稿',
    width: 1920,
    height: 1080,
    schemaVersion: 2,
    backgroundColor: '#0d1b2a',
    theme: 'midnight-blue',
    components: [],
    globalVariables: [],
    sourceMode: 'draft',
    updatedAt: '2026-03-06T10:00:00Z',
    canRead: true,
    canEdit: true,
    canPublish: true,
    canManage: true,
    publishedVersionNo: 0,
  };
}

function buildScreenList() {
  return [
    {
      id: 88,
      name: '经营驾驶舱',
      description: '发布前草稿',
      width: 1920,
      height: 1080,
      updatedAt: '2026-03-06T10:00:00Z',
      canRead: true,
      canEdit: true,
      canPublish: true,
      canManage: true,
      publishedVersionNo: 0,
    },
  ];
}

function buildPublishedScreen(screenId: string) {
  return {
    ...buildDraftScreen(screenId),
    sourceMode: 'published',
    publishedVersionNo: 3,
    publishedAt: '2026-03-06T10:05:00Z',
  };
}

function buildVersionPayload(screenId: string) {
  return {
    screen: buildPublishedScreen(screenId),
    version: {
      id: 301,
      screenId: Number(screenId),
      versionNo: 3,
      createdAt: '2026-03-06T10:05:00Z',
    },
    warmup: {
      totalDatabaseSources: 1,
      warmed: 1,
      skipped: 0,
      failed: 0,
    },
  };
}

function buildSessionDetail(state: MockState) {
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
            id: 'msg-assistant-1',
            sessionId: state.session.id,
            role: 'assistant',
            content: state.assistantMessage,
            sequenceNum: 2,
            createdAt: state.session.createdAt,
          },
        ]
      : [],
    pendingAction: state.pendingAction,
  };
}

export async function installAnalyticsAppMocks(page: Page): Promise<void> {
  const state: MockState = {
    session: null,
    pendingAction: {
      actionId: 'approval-1',
      toolId: 'validate_sql',
      params: {
        datasourceId: 'erp-demo',
        schemaName: 'ads',
        sql: 'select customer_id, customer_name, total_amount from ads_customer_sales',
      },
      reason: '需要确认 SQL 校验动作',
      riskLevel: 'MEDIUM',
    },
    prompt: '',
    assistantMessage: '已识别客户销售波动，建议先检查 Top 客户贡献和异常折扣。',
    dashboard: {
      id: 77,
      name: '客户销售经营看板',
      description: '由查询结果生成的经营看板',
      dashcards: [
        {
          id: 501,
          card_id: 42,
          row: 0,
          col: 0,
          size_x: 12,
          size_y: 6,
        },
      ],
      publicUuid: null,
    },
  };

  await page.addInitScript(() => {
    window.localStorage.setItem('dts-analytics.copilotExpanded', 'true');
  });

  await page.route('**/api/app-packs', async (route) => {
    await json(route, envelope([]));
  });

  await page.route('**/api/app-packs/pages', async (route) => {
    await json(route, envelope([]));
  });

  await page.route('**/api/ontology/app-packs', async (route) => {
    await json(route, { data: [] });
  });

  await page.route('**/api/ai/agent/sessions?*', async (route) => {
    await json(route, envelope(state.session ? [state.session] : []));
  });

  await page.route('**/api/ai/agent/sessions/*', async (route) => {
    if (route.request().method() === 'DELETE') {
      state.session = null;
      state.pendingAction = null;
      await json(route, envelope(true));
      return;
    }
    await json(route, envelope(buildSessionDetail(state)));
  });

  await page.route('**/api/ai/agent/chat', async (route) => {
    const body = await readJsonBody(route);
    const prompt = String(body.userMessage || '').trim() || '请分析客户销售波动';
    const now = nowIso();
    state.prompt = prompt;
    state.session = {
      id: 'analytics-session-1',
      title: prompt.slice(0, 12),
      createdAt: now,
      lastActiveAt: now,
    };
    await json(
      route,
      envelope({
        sessionId: state.session.id,
        agentMessage: state.assistantMessage,
        toolCalls: [],
        requiresApproval: Boolean(state.pendingAction),
        pendingAction: state.pendingAction,
      }),
    );
  });

  await page.route('**/api/ai/agent/chat/approve', async (route) => {
    state.pendingAction = null;
    state.assistantMessage = '已确认执行 SQL 校验，建议继续生成图表并发布大屏。';
    await json(
      route,
      envelope({
        sessionId: state.session?.id ?? 'analytics-session-1',
        agentMessage: state.assistantMessage,
        toolCalls: [],
        requiresApproval: false,
        pendingAction: null,
      }),
    );
  });

  await page.route('**/api/ai/agent/chat/cancel', async (route) => {
    state.pendingAction = null;
    state.assistantMessage = '已取消该动作。';
    await json(
      route,
      envelope({
        sessionId: state.session?.id ?? 'analytics-session-1',
        agentMessage: state.assistantMessage,
        toolCalls: [],
        requiresApproval: false,
        pendingAction: null,
      }),
    );
  });

  await page.route('**/api/analytics/card/*/query', async (route) => {
    await json(route, buildCardQuery());
  });

  await page.route('**/api/analytics/card', async (route) => {
    await json(route, buildCardList());
  });

  await page.route('**/api/analytics/card/*', async (route) => {
    const url = new URL(route.request().url());
    const cardId = url.pathname.split('/').filter(Boolean).at(-1) || '42';
    await json(route, buildCardDetail(cardId));
  });

  await page.route('**/api/analytics/collection', async (route) => {
    await json(route, buildCollectionList());
  });

  await page.route('**/api/analytics/dashboard/save', async (route) => {
    const body = await readJsonBody(route);
    const dashboardPayload = (body.dashboard as Record<string, unknown> | undefined) ?? {};
    const dashcardsPayload = Array.isArray(body.dashcards) ? body.dashcards : [];
    state.dashboard.name = String(dashboardPayload.name ?? state.dashboard.name ?? '客户销售经营看板');
    state.dashboard.description = String(dashboardPayload.description ?? state.dashboard.description ?? '');
    state.dashboard.dashcards = dashcardsPayload.map((dashcard, index) => {
      const row = dashcard as Record<string, unknown>;
      const cardId = Number(row.card_id || 42);
      return {
        id: Number(row.id || 501 + index),
        card_id: Number.isFinite(cardId) && cardId > 0 ? cardId : 42,
        row: Number(row.row || 0),
        col: Number(row.col || 0),
        size_x: Number(row.size_x || 12),
        size_y: Number(row.size_y || 6),
      };
    });
    await json(route, buildDashboardDetail(state));
  });

  await page.route('**/api/analytics/dashboard/*/public_link', async (route) => {
    state.dashboard.publicUuid = 'dashboard-public-uuid';
    await json(route, { uuid: state.dashboard.publicUuid });
  });

  await page.route('**/api/analytics/dashboard/*/dashcard/*/card/*/query', async (route) => {
    await json(route, buildCardQuery());
  });

  await page.route('**/api/analytics/dashboard', async (route) => {
    if (route.request().method() === 'POST') {
      const body = await readJsonBody(route);
      state.dashboard.name = String(body.name ?? state.dashboard.name ?? '客户销售经营看板');
      state.dashboard.description = String(body.description ?? state.dashboard.description ?? '');
      await json(route, buildDashboardDetail(state));
      return;
    }
    await json(route, buildDashboardList(state));
  });

  await page.route(/\/api\/analytics\/dashboard\/\d+\/params\/[^/]+\/values(?:\?.*)?$/, async (route) => {
    await json(route, []);
  });

  await page.route(/\/api\/analytics\/dashboard\/\d+(?:\?.*)?$/, async (route) => {
    const url = new URL(route.request().url());
    const dashboardId = Number(url.pathname.split('/').filter(Boolean).at(-1) || state.dashboard.id);
    if (dashboardId !== state.dashboard.id) {
      await json(route, { message: 'not found' }, 404);
      return;
    }
    await json(route, buildDashboardDetail(state));
  });

  await page.route('**/api/analytics/screens', async (route) => {
    await json(route, buildScreenList());
  });

  await page.route('**/api/analytics/screens/*/publish', async (route) => {
    const screenId = new URL(route.request().url()).pathname.split('/').filter(Boolean).at(-2) || '88';
    await json(route, buildVersionPayload(screenId));
  });

  await page.route('**/api/analytics/screens/*/public_link', async (route) => {
    await json(route, { uuid: 'screen-public-uuid' });
  });

  await page.route('**/api/analytics/screens/*', async (route) => {
    const requestUrl = new URL(route.request().url());
    const parts = requestUrl.pathname.split('/').filter(Boolean);
    const screenId = parts.at(-1) || '88';

    if (route.request().method() === 'PUT') {
      await json(route, buildDraftScreen(screenId));
      return;
    }

    const mode = requestUrl.searchParams.get('mode');
    if (mode === 'published') {
      await json(route, buildPublishedScreen(screenId));
      return;
    }

    await json(route, buildDraftScreen(screenId));
  });
}
