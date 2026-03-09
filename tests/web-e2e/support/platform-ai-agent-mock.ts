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
};

type MockState = {
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

function buildInitialState(scenario: Scenario): MockState {
  return {
    session: null,
    prompt: '',
    pendingAction: {
      actionId: 'act-1',
      toolId: scenario === 'approve' ? 'validate_sql' : 'trigger_pipeline',
      params:
        scenario === 'approve'
          ? {
              datasourceId: 'pg-main',
              schemaName: 'sales',
              sql: 'select * from sales_alerts',
            }
          : {
              dagId: 'sales-alert-dag',
              conf: { bizDate: '2026-03-06' },
            },
      reason: scenario === 'approve' ? '需要确认 SQL 校验动作' : '需要确认流水线触发动作',
      riskLevel: scenario === 'approve' ? 'MEDIUM' : 'HIGH',
    },
    assistantMessage: '已发现销售异常，建议先核查异常订单与价格折扣。',
    toolResult: JSON.stringify({
      success: true,
      textSummary: '发现 3 条异常销售记录',
      errorMessage: null,
      data: { abnormalCount: 3, status: '异常' },
    }),
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

function sseMessage(state: MockState) {
  return [
    'event: token',
    'data: {"content":"已发现销售"}',
    '',
    'event: token',
    'data: {"content":"异常"}',
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
  const request = route.request();
  const raw = request.postData() || '{}';
  return JSON.parse(raw) as Record<string, unknown>;
}

export async function installPlatformAiAgentMocks(page: Page, options: MockOptions): Promise<void> {
  const state = buildInitialState(options.scenario);

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
    const body = await readJsonBody(route);
    const prompt = String(body.userMessage || '').trim() || '请检查销售异常';
    const now = new Date().toISOString();
    state.prompt = prompt;
    state.session = {
      id: 'sess-1',
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
        sessionId: state.session?.id ?? 'sess-1',
        agentMessage: state.assistantMessage,
        toolCalls: [],
        requiresApproval: Boolean(state.pendingAction),
        pendingAction: state.pendingAction,
      }),
    );
  });

  await page.route('**/api/ai/agent/chat/approve', async (route) => {
    state.pendingAction = null;
    state.assistantMessage = '已确认执行，继续跟进异常销售处理。';
    await json(route, ok(true));
  });

  await page.route('**/api/ai/agent/chat/cancel', async (route) => {
    state.pendingAction = null;
    state.assistantMessage = '已取消该高风险动作。';
    await json(route, ok(true));
  });

  await page.route('**/api/ai/agent/feedback', async (route) => {
    await json(route, ok(true));
  });
}
