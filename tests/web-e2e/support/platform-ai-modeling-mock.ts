import type { Page, Route } from '@playwright/test';

type Scenario = 'approve' | 'cancel';

type MockOptions = {
  scenario: Scenario;
};

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
  riskLevel: 'MEDIUM' | 'HIGH';
  planSummary?: string;
  impactScope?: string;
};

type SqlModel = {
  id: string;
  planId: string;
  name: string;
  layer: string;
  sourceDataSourceId: string;
  sourceDataSourceName: string;
  sourceSystem: string;
  schemaName: string;
  description: string;
  sql: string;
  modelPath: string;
  status: string;
};

type MockState = {
  scenario: Scenario;
  session: MockSession | null;
  prompt: string;
  pendingAction: PendingAction | null;
  assistantMessage: string;
  toolResult: string;
  models: SqlModel[];
  approved: boolean;
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
  const request = route.request();
  const raw = request.postData() || '{}';
  return JSON.parse(raw) as Record<string, unknown>;
}

function nowIso(): string {
  return new Date().toISOString();
}

function buildMenuTree() {
  return [
    {
      id: 'modeling-root',
      parentId: '0',
      name: '数仓建模',
      displayName: '数仓建模',
      code: 'modeling',
      type: 1,
      path: '/modeling',
      children: [
        {
          id: 'modeling-sql',
          parentId: 'modeling-root',
          name: '逻辑建模',
          displayName: '逻辑建模',
          code: 'modeling:sql',
          type: 2,
          path: '/modeling/sql',
          component: '/pages/modeling/SqlModelingPage',
          children: [],
        },
      ],
    },
  ];
}

function buildPendingAction(): PendingAction {
  return {
    actionId: 'approve-modeling-1',
    toolId: 'trigger_pipeline',
    params: {
      dagId: 'dbt_sales_modeling',
      conf: {
        planId: 'sales-plan',
        layers: ['DWD', 'DWS'],
        datasourceId: 'erp-demo-dm',
      },
    },
    reason: 'AI 建议生成销售域模型并触发 dbt 建模流水线。',
    riskLevel: 'HIGH',
    planSummary: '生成 DWD/DWS 销售域模型草稿并写入 dbt 工作区',
    impactScope: '销售域项目空间 / models/dwd + models/dws',
  };
}

function buildModels(): SqlModel[] {
  return [
    {
      id: 'model-dwd-sales-orders',
      planId: 'sales-plan',
      name: 'dwd_sales_orders',
      layer: 'DWD',
      sourceDataSourceId: 'erp-demo-dm',
      sourceDataSourceName: 'ERP Demo DM',
      sourceSystem: 'ERP',
      schemaName: 'ads',
      description: '销售订单明细宽表',
      sql: 'select order_id, customer_id, order_amount from ods_sales_orders',
      modelPath: 'models/dwd/dwd_sales_orders.sql',
      status: 'DRAFT',
    },
    {
      id: 'model-dws-sales-summary',
      planId: 'sales-plan',
      name: 'dws_sales_summary',
      layer: 'DWS',
      sourceDataSourceId: 'erp-demo-dm',
      sourceDataSourceName: 'ERP Demo DM',
      sourceSystem: 'ERP',
      schemaName: 'ads',
      description: '销售汇总模型',
      sql: 'select customer_id, sum(order_amount) as total_amount from {{ ref(\'dwd_sales_orders\') }} group by customer_id',
      modelPath: 'models/dws/dws_sales_summary.sql',
      status: 'DRAFT',
    },
  ];
}

function buildInitialState(scenario: Scenario): MockState {
  return {
    scenario,
    session: null,
    prompt: '',
    pendingAction: buildPendingAction(),
    assistantMessage: '已生成销售域建模建议，建议在确认后创建 DWD 与 DWS 模型。',
    toolResult: JSON.stringify({
      success: true,
      textSummary: '已规划 2 个销售域模型草稿',
      data: {
        planId: 'sales-plan',
        suggestedModels: ['dwd_sales_orders', 'dws_sales_summary'],
      },
      errorMessage: null,
    }),
    models: [],
    approved: false,
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
            id: 'msg-tool-1',
            sessionId: state.session.id,
            role: 'tool',
            toolName: 'generate_sql_models',
            toolParams: JSON.stringify({
              planId: 'sales-plan',
              layers: ['DWD', 'DWS'],
            }),
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

function sseMessage(state: MockState) {
  return [
    'event: token',
    'data: {"content":"已生成销售域"}',
    '',
    'event: token',
    'data: {"content":"建模建议"}',
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

function buildDbtConfig() {
  return {
    enabled: true,
    config: {
      projectDir: '/opt/dts/dbt-project',
      profilesDir: '/opt/dts/dbt-profiles',
      profileName: 'default',
      targetName: 'dev',
      targetDataSourceId: 'erp-demo-dm',
      schema: 'ads',
    },
    profileStatus: {
      generated: true,
      message: 'profile 已生成',
      profilePath: '/opt/dts/dbt-profiles/profiles.yml',
    },
    workspaceStatus: {
      ok: true,
      message: 'dbt 工作区可用',
    },
    target: {
      id: 'erp-demo-dm',
      name: 'ERP Demo DM',
      type: 'dm',
    },
  };
}

function buildSyncStatus(state: MockState) {
  if (!state.approved) {
    return {
      manifest: { synced: true, lastSyncAt: '2026-03-06T09:00:00Z' },
      runResults: { synced: true, lastSyncAt: '2026-03-06T09:00:00Z' },
      stats: {
        lastSyncAt: '2026-03-06T09:00:00Z',
        datasetsCreated: 0,
        datasetsUpdated: 0,
        columnsUpdated: 0,
        lineageCreated: 0,
        lineageRemoved: 0,
      },
      latestRun: {
        present: false,
        status: 'IDLE',
        total: 0,
        success: 0,
        failed: 0,
        skipped: 0,
        failures: [],
      },
    };
  }

  return {
    manifest: { synced: true, lastSyncAt: '2026-03-06T09:10:00Z' },
    runResults: { synced: true, lastSyncAt: '2026-03-06T09:10:00Z' },
    stats: {
      lastSyncAt: '2026-03-06T09:10:00Z',
      datasetsCreated: 2,
      datasetsUpdated: 0,
      columnsUpdated: 5,
      lineageCreated: 2,
      lineageRemoved: 0,
    },
    latestRun: {
      present: true,
      status: 'SUCCESS',
      total: 2,
      success: 2,
      failed: 0,
      skipped: 0,
      failures: [],
    },
  };
}

function buildRuns(state: MockState) {
  if (!state.approved) {
    return [];
  }

  return [
    {
      dag_run_id: 'dbt_sales_modeling__2026-03-06T09:10:00Z',
      state: 'success',
      conf: {
        operation: 'run',
        models: 'tag:sales',
        target: 'dev',
      },
      execution_date: '2026-03-06T09:10:00Z',
      start_date: '2026-03-06T09:10:01Z',
      end_date: '2026-03-06T09:10:15Z',
    },
  ];
}

function buildModelColumns(modelId: string) {
  if (modelId === 'model-dws-sales-summary') {
    return [
      { name: 'CUSTOMER_ID', dataType: 'BIGINT', status: 'ACTIVE' },
      { name: 'TOTAL_AMOUNT', dataType: 'DECIMAL(18,2)', status: 'ACTIVE' },
    ];
  }

  return [
    { name: 'ORDER_ID', dataType: 'BIGINT', status: 'ACTIVE' },
    { name: 'CUSTOMER_ID', dataType: 'BIGINT', status: 'ACTIVE' },
    { name: 'ORDER_AMOUNT', dataType: 'DECIMAL(18,2)', status: 'ACTIVE' },
  ];
}

function buildContractImpact() {
  return {
    blocking: false,
    downstreamDatasetCount: 0,
    downstreamMetricCount: 0,
    messages: [],
  };
}

export async function installPlatformAiModelingMocks(page: Page, options: MockOptions): Promise<void> {
  const state = buildInitialState(options.scenario);

  await page.route('**/api/menu/tree', async (route) => {
    await json(route, ok(buildMenuTree()));
  });

  await page.route('**/api/menu', async (route) => {
    await json(route, ok(buildMenuTree()));
  });

  await page.route('**/api/app-packs', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/app-packs/pages', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/workbench/overview', async (route) => {
    await json(route, ok({}));
  });

  await page.route('**/api/workbench/leader-overview?*', async (route) => {
    await json(route, ok({}));
  });

  await page.route('**/api/ai/agent/sessions?*', async (route) => {
    await json(route, ok(state.session ? [state.session] : []));
  });

  await page.route('**/api/ai/agent/sessions/*', async (route) => {
    await json(route, ok(buildSessionDetail(state)));
  });

  await page.route('**/api/ai/agent/chat/stream', async (route) => {
    const body = await readJsonBody(route);
    const prompt = String(body.userMessage || '').trim() || '请帮我生成销售域模型';
    const now = nowIso();
    state.prompt = prompt;
    state.session = {
      id: 'sess-modeling-1',
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
    await json(
      route,
      ok({
        sessionId: state.session?.id ?? 'sess-modeling-1',
        agentMessage: state.assistantMessage,
        toolCalls: [],
        requiresApproval: Boolean(state.pendingAction),
        pendingAction: state.pendingAction,
      }),
    );
  });

  await page.route('**/api/ai/agent/chat/approve', async (route) => {
    state.pendingAction = null;
    state.approved = true;
    state.models = buildModels();
    state.assistantMessage = '已确认执行，销售域模型已生成并触发 dbt 运行。';
    state.toolResult = JSON.stringify({
      success: true,
      textSummary: '已创建 2 个模型并触发 dbt run',
      data: {
        createdModels: state.models.map((item) => item.name),
      },
      errorMessage: null,
    });
    await json(route, ok(true));
  });

  await page.route('**/api/ai/agent/chat/cancel', async (route) => {
    state.pendingAction = null;
    state.approved = false;
    state.models = [];
    state.assistantMessage = '已取消本次建模执行。';
    await json(route, ok(true));
  });

  await page.route('**/api/ai/agent/feedback', async (route) => {
    await json(route, ok(true));
  });

  await page.route('**/api/ai/copilot/status', async (route) => {
    await json(route, ok({ available: true }));
  });

  await page.route('**/api/etl/dbt/config', async (route) => {
    await json(route, ok(buildDbtConfig()));
  });

  await page.route('**/api/etl/dbt/sync/status', async (route) => {
    await json(route, ok(buildSyncStatus(state)));
  });

  await page.route('**/api/etl/dbt/runs?*', async (route) => {
    await json(route, ok(buildRuns(state)));
  });

  await page.route('**/api/modeling/plans**', async (route) => {
    await json(
      route,
      ok([
        {
          id: 'sales-plan',
          name: '销售域',
          domain: '经营分析',
        },
      ]),
    );
  });

  await page.route('**/api/modeling/templates/layers', async (route) => {
    await json(
      route,
      ok([
        { layer: 'DWD', name: '明细层', description: '订单明细宽表' },
        { layer: 'DWS', name: '汇总层', description: '销售汇总层' },
        { layer: 'ADS', name: '应用层', description: '应用层' },
      ]),
    );
  });

  await page.route('**/api/modeling/sql-models/dbt/sources**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/modeling/sql-models/dbt/refs**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/modeling/sql-models/*/columns', async (route) => {
    const modelId = new URL(route.request().url()).pathname.split('/').filter(Boolean).at(-2) || '';
    await json(route, ok(buildModelColumns(modelId)));
  });

  await page.route('**/api/modeling/sql-models/*/contract-impact', async (route) => {
    await json(route, ok(buildContractImpact()));
  });

  await page.route('**/api/modeling/sql-models**', async (route) => {
    await json(route, ok(state.models));
  });

  await page.route('**/api/infra/data-sources**', async (route) => {
    await json(
      route,
      ok([
        {
          id: 'erp-demo-dm',
          name: 'ERP Demo DM',
          type: 'dm',
          jdbcUrl: 'jdbc:dm://10.20.0.4:5236',
          username: 'ERPDEMO',
          status: 'ONLINE',
          lastVerifiedAt: '2026-03-06T09:00:00Z',
        },
      ]),
    );
  });
}
