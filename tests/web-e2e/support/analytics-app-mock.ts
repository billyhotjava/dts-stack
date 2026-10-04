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

type MockScreenDetail = {
  id: number;
  name: string;
  description: string;
  width: number;
  height: number;
  schemaVersion: number;
  backgroundColor: string;
  backgroundImage?: string | null;
  theme?: string | null;
  components: Array<Record<string, unknown>>;
  globalVariables: Array<Record<string, unknown>>;
  sourceMode: 'draft' | 'published';
  updatedAt: string;
  canRead: boolean;
  canEdit: boolean;
  canPublish: boolean;
  canManage: boolean;
  publishedVersionNo: number;
  publishedAt?: string | null;
  publicUuid?: string | null;
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
  screens: Record<string, MockScreenDetail>;
  nextScreenId: number;
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

function buildDraftScreen(screenId: string, overrides: Partial<MockScreenDetail> = {}): MockScreenDetail {
  return {
    id: Number(screenId),
    name: '经营驾驶舱',
    description: '发布前草稿',
    width: 1920,
    height: 1080,
    schemaVersion: 2,
    backgroundColor: '#f8fafc',
    backgroundImage: null,
    theme: 'glacier',
    components: [],
    globalVariables: [],
    sourceMode: 'draft',
    updatedAt: '2026-03-06T10:00:00Z',
    canRead: true,
    canEdit: true,
    canPublish: true,
    canManage: true,
    publishedVersionNo: 0,
    publishedAt: null,
    publicUuid: null,
    ...overrides,
  };
}

function buildScreenList(state: MockState) {
  return Object.values(state.screens)
    .sort((a, b) => b.id - a.id)
    .map((screen) => ({
      id: screen.id,
      name: screen.name,
      description: screen.description,
      width: screen.width,
      height: screen.height,
      updatedAt: screen.updatedAt,
      canRead: screen.canRead,
      canEdit: screen.canEdit,
      canPublish: screen.canPublish,
      canManage: screen.canManage,
      publishedVersionNo: screen.publishedVersionNo,
      publishedAt: screen.publishedAt,
      public_uuid: screen.publicUuid ?? null,
    }));
}

function buildPublishedScreen(screenId: string, overrides: Partial<MockScreenDetail> = {}): MockScreenDetail {
  return {
    ...buildDraftScreen(screenId, overrides),
    sourceMode: 'published',
    publishedVersionNo: overrides.publishedVersionNo ?? 3,
    publishedAt: overrides.publishedAt ?? '2026-03-06T10:05:00Z',
  };
}

function buildVersionPayload(screen: MockScreenDetail) {
  return {
    screen,
    version: {
      id: 301,
      screenId: Number(screen.id),
      versionNo: screen.publishedVersionNo || 3,
      createdAt: screen.publishedAt || '2026-03-06T10:05:00Z',
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

function getScreenOrDefault(state: MockState, screenId: string): MockScreenDetail {
  return state.screens[screenId] ?? buildDraftScreen(screenId);
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
    screens: {
      '88': buildDraftScreen('88'),
    },
    nextScreenId: 901,
  };

  await page.addInitScript(() => {
    window.localStorage.setItem('dts-analytics.copilotExpanded', 'true');
  });

  await page.route('**/analytics/api/app-packs', async (route) => {
    await json(route, envelope([]));
  });

  await page.route('**/analytics/api/app-packs/pages', async (route) => {
    await json(route, envelope([]));
  });

  await page.route('**/analytics/api/ontology/app-packs', async (route) => {
    await json(route, { data: [] });
  });

  await page.route('**/analytics/api/ai/agent/sessions?*', async (route) => {
    await json(route, envelope(state.session ? [state.session] : []));
  });

  await page.route('**/analytics/api/ai/agent/sessions/*', async (route) => {
    if (route.request().method() === 'DELETE') {
      state.session = null;
      state.pendingAction = null;
      await json(route, envelope(true));
      return;
    }
    await json(route, envelope(buildSessionDetail(state)));
  });

  await page.route('**/analytics/api/ai/agent/chat', async (route) => {
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

  await page.route('**/analytics/api/ai/agent/chat/approve', async (route) => {
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

  await page.route('**/analytics/api/ai/agent/chat/cancel', async (route) => {
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

  await page.route('**/analytics/api/card/*/query', async (route) => {
    await json(route, buildCardQuery());
  });

  await page.route('**/analytics/api/card', async (route) => {
    await json(route, buildCardList());
  });

  await page.route('**/analytics/api/card/*', async (route) => {
    const url = new URL(route.request().url());
    const cardId = url.pathname.split('/').filter(Boolean).at(-1) || '42';
    await json(route, buildCardDetail(cardId));
  });

  await page.route('**/analytics/api/collection', async (route) => {
    await json(route, buildCollectionList());
  });

  await page.route('**/analytics/api/screen-templates*', async (route) => {
    if (route.request().method() === 'GET') {
      await json(route, []);
      return;
    }
    await json(route, { message: 'not supported in mock' }, 404);
  });

  await page.route('**/analytics/api/dashboard/save', async (route) => {
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

  await page.route('**/analytics/api/dashboard/*/public_link', async (route) => {
    state.dashboard.publicUuid = 'dashboard-public-uuid';
    await json(route, { uuid: state.dashboard.publicUuid });
  });

  await page.route('**/analytics/api/dashboard/*/dashcard/*/card/*/query', async (route) => {
    await json(route, buildCardQuery());
  });

  await page.route('**/analytics/api/dashboard', async (route) => {
    if (route.request().method() === 'POST') {
      const body = await readJsonBody(route);
      state.dashboard.name = String(body.name ?? state.dashboard.name ?? '客户销售经营看板');
      state.dashboard.description = String(body.description ?? state.dashboard.description ?? '');
      await json(route, buildDashboardDetail(state));
      return;
    }
    await json(route, buildDashboardList(state));
  });

  await page.route(/\/analytics\/api\/dashboard\/\d+\/params\/[^/]+\/values(?:\?.*)?$/, async (route) => {
    await json(route, []);
  });

  await page.route(/\/analytics\/api\/dashboard\/\d+(?:\?.*)?$/, async (route) => {
    const url = new URL(route.request().url());
    const dashboardId = Number(url.pathname.split('/').filter(Boolean).at(-1) || state.dashboard.id);
    if (dashboardId !== state.dashboard.id) {
      await json(route, { message: 'not found' }, 404);
      return;
    }
    await json(route, buildDashboardDetail(state));
  });

  await page.route('**/analytics/api/screens', async (route) => {
    if (route.request().method() === 'POST') {
      const body = await readJsonBody(route);
      const screenId = String(state.nextScreenId++);
      const created = buildDraftScreen(screenId, {
        name: String(body.name ?? '未命名大屏'),
        description: String(body.description ?? ''),
        width: Number(body.width || 1920),
        height: Number(body.height || 1080),
        schemaVersion: Number(body.schemaVersion || 2),
        backgroundColor: String(body.backgroundColor || '#f8fafc'),
        backgroundImage: typeof body.backgroundImage === 'string' ? body.backgroundImage : null,
        theme: typeof body.theme === 'string' ? body.theme : 'glacier',
        components: Array.isArray(body.components) ? (body.components as Array<Record<string, unknown>>) : [],
        globalVariables: Array.isArray(body.globalVariables) ? (body.globalVariables as Array<Record<string, unknown>>) : [],
        updatedAt: nowIso(),
      });
      state.screens[screenId] = created;
      await json(route, created);
      return;
    }
    await json(route, buildScreenList(state));
  });

  await page.route('**/analytics/api/screens/*/publish', async (route) => {
    const screenId = new URL(route.request().url()).pathname.split('/').filter(Boolean).at(-2) || '88';
    const current = getScreenOrDefault(state, screenId);
    const published = buildPublishedScreen(screenId, {
      ...current,
      updatedAt: nowIso(),
      publishedVersionNo: Math.max(1, Number(current.publishedVersionNo || 0) + 1),
      publishedAt: nowIso(),
    });
    state.screens[screenId] = published;
    await json(route, buildVersionPayload(published));
  });

  await page.route('**/analytics/api/screens/*/public_link', async (route) => {
    const screenId = new URL(route.request().url()).pathname.split('/').filter(Boolean).at(-2) || '88';
    const current = getScreenOrDefault(state, screenId);
    const publicUuid = `screen-public-${screenId}`;
    state.screens[screenId] = { ...current, publicUuid };
    await json(route, { uuid: publicUuid });
  });

  await page.route('**/analytics/api/public/screen/*', async (route) => {
    const publicUuid = new URL(route.request().url()).pathname.split('/').filter(Boolean).at(-1) || '';
    const screen = Object.values(state.screens).find((item) => item.publicUuid === publicUuid);
    if (!screen) {
      await json(route, { message: 'not found' }, 404);
      return;
    }
    await json(route, buildPublishedScreen(String(screen.id), screen));
  });

  await page.route('**/analytics/api/screens/*', async (route) => {
    const requestUrl = new URL(route.request().url());
    const parts = requestUrl.pathname.split('/').filter(Boolean);
    const screenId = parts.at(-1) || '88';

    if (route.request().method() === 'PUT') {
      const body = await readJsonBody(route);
      const current = getScreenOrDefault(state, screenId);
      const updated = buildDraftScreen(screenId, {
        ...current,
        name: typeof body.name === 'string' ? body.name : current.name,
        description: typeof body.description === 'string' ? body.description : current.description,
        width: typeof body.width === 'number' ? body.width : current.width,
        height: typeof body.height === 'number' ? body.height : current.height,
        schemaVersion: typeof body.schemaVersion === 'number' ? body.schemaVersion : current.schemaVersion,
        backgroundColor: typeof body.backgroundColor === 'string' ? body.backgroundColor : current.backgroundColor,
        backgroundImage: typeof body.backgroundImage === 'string' ? body.backgroundImage : (body.backgroundImage === null ? null : current.backgroundImage),
        theme: typeof body.theme === 'string' ? body.theme : current.theme,
        components: Array.isArray(body.components) ? (body.components as Array<Record<string, unknown>>) : current.components,
        globalVariables: Array.isArray(body.globalVariables) ? (body.globalVariables as Array<Record<string, unknown>>) : current.globalVariables,
        updatedAt: nowIso(),
        publishedVersionNo: current.publishedVersionNo,
        publishedAt: current.publishedAt,
        publicUuid: current.publicUuid,
      });
      state.screens[screenId] = updated;
      await json(route, updated);
      return;
    }

    if (route.request().method() === 'DELETE') {
      delete state.screens[screenId];
      await json(route, {});
      return;
    }

    const current = getScreenOrDefault(state, screenId);
    const mode = requestUrl.searchParams.get('mode');
    if (mode === 'published' && current.publishedVersionNo > 0) {
      await json(route, buildPublishedScreen(screenId, current));
      return;
    }

    await json(route, current);
  });
}
