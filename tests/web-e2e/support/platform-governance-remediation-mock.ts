import type { Page, Route } from '@playwright/test';

type MockSession = {
  id: string;
  title: string;
  createdAt: string;
  lastActiveAt: string;
};

type AnomalyRecord = {
  id: string;
  columnName: string;
  anomalyType: string;
  severity: string;
  metricName: string;
  currentValue: number;
  expectedRange: string;
  deviationPct: number;
  status: string;
  description: string;
  aiAnalysis: string;
  recommendedRuleJson: string;
};

type MockState = {
  anomalies: AnomalyRecord[];
  session: MockSession | null;
  assistantMessage: string;
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

async function routeApi(
  page: Page,
  pathPattern: string,
  handler: (route: Route) => Promise<void> | void,
): Promise<void> {
  for (const pattern of [`**/api${pathPattern}`, `**/platform/api${pathPattern}`]) {
    await page.route(pattern, handler);
  }
}

function nowIso(): string {
  return new Date().toISOString();
}

function buildInitialState(): MockState {
  return {
    anomalies: [
      {
        id: 'anom-1',
        columnName: 'discount_rate',
        anomalyType: 'SPIKE',
        severity: 'HIGH',
        metricName: 'discount_avg',
        currentValue: 0.37,
        expectedRange: '0.05 - 0.15',
        deviationPct: 146.7,
        status: 'NEW',
        description: '促销折扣率突然升高，疑似配置错误。',
        aiAnalysis: 'AI 判断主要由促销规则失配导致，建议先修复折扣配置并回补当天快照。',
        recommendedRuleJson: JSON.stringify(
          { ruleType: 'threshold', column: 'discount_rate', max: 0.15 },
          null,
          2,
        ),
      },
    ],
    session: null,
    assistantMessage: 'AI 复核结论：异常已缓解，建议继续观察下一批次。',
  };
}

function buildMenuTree() {
  return [
    {
      id: 'governance-root',
      parentId: '0',
      name: '数据治理中心',
      displayName: '数据治理中心',
      code: 'governance',
      type: 1,
      path: '/governance',
      children: [
        {
          id: 'governance-quality',
          parentId: 'governance-root',
          name: '质量报告',
          displayName: '质量报告',
          code: 'governance:quality',
          type: 2,
          path: '/governance/quality',
          component: '/pages/governance/QualityReportPage',
          children: [],
        },
      ],
    },
  ];
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
            content: '请复核折扣率异常是否已经修复',
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
    pendingAction: null,
  };
}

function sseMessage(state: MockState) {
  return [
    'event: token',
    'data: {"content":"AI 复核"}',
    '',
    'event: message',
    `data: ${JSON.stringify({
      sessionId: state.session?.id,
      content: state.assistantMessage,
      pendingAction: null,
    })}`,
    '',
    'event: done',
    'data: {}',
    '',
  ].join('\n');
}

export async function installPlatformGovernanceRemediationMocks(page: Page): Promise<void> {
  const state = buildInitialState();

  await routeApi(page, '/menu/tree', async route => {
    await json(route, ok(buildMenuTree()));
  });

  await routeApi(page, '/menu', async route => {
    await json(route, ok(buildMenuTree()));
  });

  await routeApi(page, '/app-packs', async route => {
    await json(route, ok([]));
  });

  await routeApi(page, '/app-packs/pages', async route => {
    await json(route, ok([]));
  });

  await routeApi(page, '/governance/quality/rules/page?*', async route => {
    await json(route, ok({ content: [], total: 12 }));
  });

  await routeApi(page, '/governance/issues/page?*', async route => {
    await json(route, ok({ content: [], total: 3 }));
  });

  await routeApi(page, '/governance/compliance/batches/page?*', async route => {
    await json(route, ok({ content: [], total: 1 }));
  });

  await routeApi(page, '/governance/ops/overview?*', async route => {
    await json(route, ok({
      kpi: {
        qualitySuccessRate: 97.2,
        issueOverdueRate: 3.1,
      },
    }));
  });

  await routeApi(page, '/governance/ops/trend?*', async route => {
    await json(route, ok([
      { date: '03-05', qualitySuccessRate: 96.1, issueOverdueRate: 4.3, qualityRunCount: 18, issueOpenCount: 3 },
      { date: '03-06', qualitySuccessRate: 96.8, issueOverdueRate: 3.8, qualityRunCount: 19, issueOpenCount: 2 },
      { date: '03-07', qualitySuccessRate: 97.2, issueOverdueRate: 3.1, qualityRunCount: 22, issueOpenCount: 1 },
    ]));
  });

  await routeApi(page, '/governance/ops/release-gate?*', async route => {
    await json(route, ok({
      readyForRelease: false,
      checks: [{ code: 'quality-failed', name: '失败规则清零', passed: false, actual: 1, threshold: '0', severity: 'HIGH' }],
    }));
  });

  await routeApi(page, '/catalog/datasets?*', async route => {
    await json(route, ok({
      content: [{ id: 'ds-sales-orders', name: 'ERP 销售订单' }],
      total: 1,
    }));
  });

  await routeApi(page, '/governance/quality/anomaly/profile', async route => {
    await json(route, ok({ anomalyCount: state.anomalies.length }));
  });

  await routeApi(page, '/governance/quality/anomaly/anomalies/*', async route => {
    await json(route, ok(state.anomalies));
  });

  await routeApi(page, '/governance/quality/anomaly/*/status?*', async route => {
    const url = new URL(route.request().url());
    const nextStatus = url.searchParams.get('status') || 'ACKNOWLEDGED';
    state.anomalies = state.anomalies.map(item =>
      item.id === 'anom-1' ? { ...item, status: nextStatus } : item,
    );
    await json(route, ok({ success: true }));
  });

  await routeApi(page, '/ai/agent/sessions?*', async route => {
    await json(route, ok(state.session ? [state.session] : []));
  });

  await routeApi(page, '/ai/agent/sessions/*', async route => {
    if (route.request().method() === 'DELETE') {
      state.session = null;
      await json(route, ok(true));
      return;
    }
    await json(route, ok(buildSessionDetail(state)));
  });

  await routeApi(page, '/ai/agent/chat/stream', async route => {
    const now = nowIso();
    state.session = {
      id: 'sess-governance-1',
      title: '治理复核',
      createdAt: now,
      lastActiveAt: now,
    };

    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream; charset=utf-8',
      headers: {
        'cache-control': 'no-cache',
        connection: 'keep-alive',
      },
      body: sseMessage(state),
    });
  });

  await routeApi(page, '/ai/agent/chat', async route => {
    await json(route, ok({
      sessionId: state.session?.id ?? 'sess-governance-1',
      agentMessage: state.assistantMessage,
      toolCalls: [],
      requiresApproval: false,
      pendingAction: null,
    }));
  });

  await routeApi(page, '/ai/agent/feedback', async route => {
    await json(route, ok(true));
  });
}
