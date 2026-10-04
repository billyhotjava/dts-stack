import type { Page, Route } from '@playwright/test';

type IngestionTaskState = {
  task: Record<string, unknown>;
  latestExecution: Record<string, unknown> | null;
  executePollCount: number;
  executionSubmitted: boolean;
};

type IngestionExecutionRecord = Record<string, unknown>;

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
function nowIso(): string {
  return new Date().toISOString();
}

export async function installPlatformEltShellMocks(page: Page): Promise<void> {
  const menuTree = [
    {
      id: 'catalog-explore',
      type: 'CATALOGUE',
      path: '/explore',
      name: '数据接入',
      children: [
        {
          id: 'menu-transform',
          type: 'MENU',
          path: '/explore/etl/transform',
          name: '入湖任务中心',
          component: '/pages/explore/etl/TransformPage',
        },
      ],
    },
    {
      id: 'catalog-modeling',
      type: 'CATALOGUE',
      path: '/modeling',
      name: '数据开发',
      children: [
        {
          id: 'menu-modeling-sql',
          type: 'MENU',
          path: '/modeling/sql',
          name: '逻辑建模',
          component: '/pages/modeling/SqlModelingPage',
        },
      ],
    },
  ];

  await page.route('**/api/menu/tree**', async (route) => {
    await json(route, ok(menuTree));
  });

  await page.route('**/api/app-packs', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/app-packs/pages', async (route) => {
    await json(route, ok([]));
  });
}

export async function installPlatformIngestionCenterMocks(page: Page): Promise<void> {
  await installPlatformEltShellMocks(page);

  const state: IngestionTaskState = {
    task: {
      id: 101,
      name: 'ERP 销售订单入湖',
      description: 'ERP 销售订单同步到 pg-lake',
      sourceType: 'dmreader',
      sourceConfig: {
        dataSourceId: 'ds-erp',
        table: 'ERPDMO.ORDERS',
      },
      sourceDataSourceId: 'ds-erp',
      destinationType: 'postgresqlwriter',
      destinationConfig: {
        table: 'ods_erp_orders',
      },
      syncMode: 'full_refresh',
      syncSchedule: 'manual',
      airflowEnabled: true,
      airflowDagId: 'ingestion_erp_sales_orders',
      status: 'active',
      lastExecutedAt: '2026-03-22T12:00:00Z',
      lastExecutionStatus: 'success',
      createdBy: 'opadmin',
      createdDate: '2026-03-22T11:00:00Z',
      lastModifiedBy: 'opadmin',
      lastModifiedDate: '2026-03-22T11:30:00Z',
    },
    latestExecution: {
      id: 9001,
      taskId: 101,
      taskName: 'ERP 销售订单入湖',
      executionId: 'ingestion-run-9001',
      status: 'success',
      startTime: '2026-03-22T12:00:00Z',
      endTime: '2026-03-22T12:03:00Z',
      rowsRead: 1280,
      rowsWritten: 1280,
      logPath: '/opt/airflow/logs/ingestion_erp_sales_orders.log',
    },
    executePollCount: 0,
    executionSubmitted: false,
  };

  await page.route('**/ingestion/tasks/list**', async (route) => {
    await json(
      route,
      ok({
        content: [state.task],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0,
      }),
    );
  });

  await page.route('**/ingestion/tasks/101/execute/async', async (route) => {
    state.executionSubmitted = true;
    state.executePollCount = 0;
    state.latestExecution = {
      id: 9002,
      taskId: 101,
      taskName: 'ERP 销售订单入湖',
      executionId: 'ingestion-run-9002',
      status: 'preparing',
      startTime: nowIso(),
      rowsRead: 0,
      rowsWritten: 0,
    };
    await json(route, ok({
      taskId: 101,
      taskName: 'ERP 销售订单入湖',
      status: 'SUBMITTED',
      async: true,
      pollIntervalMs: 1000,
      message: '任务已提交',
    }));
  });

  await page.route('**/ingestion/tasks/101/executions/latest', async (route) => {
    if (state.executionSubmitted && state.latestExecution) {
      state.executePollCount += 1;
      if (state.executePollCount >= 2) {
        state.latestExecution = {
          ...state.latestExecution,
          status: 'success',
          endTime: nowIso(),
          rowsRead: 2048,
          rowsWritten: 2048,
        };
        state.task = {
          ...state.task,
          lastExecutionStatus: 'success',
          lastExecutedAt: nowIso(),
        };
      }
    }
    await json(route, ok(state.latestExecution));
  });

  await page.route('**/ingestion/tasks/101/executions/*/logs**', async (route) => {
    await json(route, ok({
      taskId: 101,
      executionId: 9002,
      dagId: 'ingestion_erp_sales_orders',
      dagRunId: 'manual__2026-03-22T16:00:00+00:00',
      log: [
        '连接源端数据源 ds-erp 成功',
        '生成 Addax 作业成功',
        '写入目标表 ods_erp_orders 完成',
      ].join('\n'),
    }));
  });

  await page.route('**/ingestion/tasks/101', async (route) => {
    await json(route, ok(state.task));
  });

  await page.route('**/infra/data-sources/ds-erp', async (route) => {
    await json(route, ok({
      id: 'ds-erp',
      name: 'ERP Demo DM',
      type: 'DM8',
      jdbcUrl: 'jdbc:dm://127.0.0.1:5236/ERPDMO',
      username: 'SYSDBA',
      status: 'ACTIVE',
    }));
  });
}

export async function installPlatformIngestionEdgeMocks(page: Page): Promise<void> {
  await installPlatformEltShellMocks(page);

  const state = {
    task: {
      id: 101,
      name: 'ERP 销售订单入湖',
      description: 'ERP 销售订单同步到 pg-lake',
      sourceType: 'dmreader',
      sourceConfig: {
        dataSourceId: 'ds-erp',
        table: 'ERPDMO.ORDERS',
      },
      sourceDataSourceId: 'ds-erp',
      destinationType: 'postgresqlwriter',
      destinationConfig: {
        table: 'ods_erp_orders',
      },
      syncMode: 'full_refresh',
      syncSchedule: 'manual',
      airflowEnabled: true,
      airflowDagId: 'ingestion_erp_sales_orders',
      status: 'active',
      lastExecutedAt: '2026-03-22T12:00:00Z',
      lastExecutionStatus: 'failed',
      createdBy: 'opadmin',
      createdDate: '2026-03-22T11:00:00Z',
      lastModifiedBy: 'opadmin',
      lastModifiedDate: '2026-03-22T11:30:00Z',
    } as Record<string, unknown>,
    executions: [
      {
        id: 9001,
        taskId: 101,
        taskName: 'ERP 销售订单入湖',
        executionId: 'ingestion-run-9001',
        status: 'failed',
        triggerMode: 'MANUAL',
        startTime: '2026-03-22T12:00:00Z',
        endTime: '2026-03-22T12:03:00Z',
        rowsRead: 1024,
        rowsWritten: 0,
        failureCategory: 'RUNTIME_ERROR',
        failureAdvice: '请重建 DAG 后重试',
        errorMessage: 'Airflow DAG 未就绪',
        createdAt: '2026-03-22T12:03:00Z',
      } as IngestionExecutionRecord,
    ],
    latestExecution: null as IngestionExecutionRecord | null,
    activeMode: null as 'retry' | 'execute' | null,
    activePollCount: 0,
    nextId: 9002,
    rebuildCount: 0,
  };
  state.latestExecution = state.executions[0];

  const sortExecutions = () => {
    state.executions.sort((left, right) => {
      const leftTime = String(left.createdAt || left.startTime || '');
      const rightTime = String(right.createdAt || right.startTime || '');
      return rightTime.localeCompare(leftTime);
    });
  };

  const scheduleExecution = (mode: 'retry' | 'execute') => {
    const nextId = state.nextId++;
    const now = nowIso();
    const triggerMode = mode === 'retry' ? 'FAILED_ONLY' : 'MANUAL';
    const execution: IngestionExecutionRecord = {
      id: nextId,
      taskId: 101,
      taskName: 'ERP 销售订单入湖',
      executionId: `ingestion-run-${nextId}`,
      status: 'preparing',
      triggerMode,
      startTime: now,
      rowsRead: 0,
      rowsWritten: 0,
      createdAt: now,
    };
    state.executions = [execution, ...state.executions];
    state.latestExecution = execution;
    state.activeMode = mode;
    state.activePollCount = 0;
    state.task = {
      ...state.task,
      lastExecutionStatus: 'running',
      lastExecutedAt: now,
      lastModifiedDate: now,
    };
  };

  const settleActiveExecution = () => {
    if (!state.latestExecution) {
      return;
    }
    const now = nowIso();
    state.latestExecution = {
      ...state.latestExecution,
      status: 'success',
      endTime: now,
      rowsRead: state.activeMode === 'retry' ? 1024 : 2048,
      rowsWritten: state.activeMode === 'retry' ? 1024 : 2048,
      errorMessage: '',
      failureCategory: '',
      failureAdvice: '',
      createdAt: now,
    };
    state.executions = state.executions.map((item) =>
      item.id === state.latestExecution?.id ? state.latestExecution! : item,
    );
    sortExecutions();
    state.task = {
      ...state.task,
      lastExecutionStatus: 'success',
      lastExecutedAt: now,
      lastModifiedDate: now,
    };
    state.activeMode = null;
  };

  await page.route('**/ingestion/tasks/list**', async (route) => {
    await json(
      route,
      ok({
        content: [state.task],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0,
      }),
    );
  });

  await page.route('**/ingestion/tasks/101/executions/latest', async (route) => {
    if (state.activeMode && state.latestExecution) {
      state.activePollCount += 1;
      if (state.activePollCount >= 2) {
        settleActiveExecution();
      }
    }
    await json(route, ok(state.latestExecution));
  });

  await page.route('**/ingestion/tasks/101/executions/9001/retry/async**', async (route) => {
    scheduleExecution('retry');
    await json(route, ok({
      taskId: 101,
      taskName: 'ERP 销售订单入湖',
      status: 'SUBMITTED',
      async: true,
      pollIntervalMs: 1000,
      message: '已提交失败重试任务',
    }));
  });

  await page.route('**/ingestion/tasks/101/execute/async', async (route) => {
    scheduleExecution('execute');
    await json(route, ok({
      taskId: 101,
      taskName: 'ERP 销售订单入湖',
      status: 'SUBMITTED',
      async: true,
      pollIntervalMs: 1000,
      message: '任务已提交',
    }));
  });

  await page.route('**/ingestion/tasks/101/dag/rebuild', async (route) => {
    state.rebuildCount += 1;
    state.task = {
      ...state.task,
      airflowDagId: `ingestion_erp_sales_orders_v${state.rebuildCount + 1}`,
      lastModifiedDate: nowIso(),
    };
    await json(route, ok(state.task));
  });

  await page.route('**/ingestion/tasks/101/executions**', async (route) => {
    await json(
      route,
      ok({
        content: state.executions,
        totalElements: state.executions.length,
        totalPages: 1,
        size: 20,
        number: 0,
      }),
    );
  });

  await page.route('**/ingestion/tasks/101/executions/*/logs**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    const executionId = Number(pathname.split('/').slice(-2, -1)[0] || 0);
    const content =
      executionId === 9001
        ? ['Airflow DAG 未就绪', '请重建 DAG 后重试'].join('\n')
        : ['连接源端数据源 ds-erp 成功', '生成 Addax 作业成功', '写入目标表 ods_erp_orders 完成'].join('\n');
    await json(route, ok({
      taskId: 101,
      executionId,
      dagId: String(state.task.airflowDagId || 'ingestion_erp_sales_orders'),
      dagRunId: `manual__${executionId}`,
      log: content,
    }));
  });

  await page.route('**/ingestion/tasks/101', async (route) => {
    await json(route, ok(state.task));
  });

  await page.route('**/infra/data-sources/ds-erp', async (route) => {
    await json(route, ok({
      id: 'ds-erp',
      name: 'ERP Demo DM',
      type: 'DM8',
      jdbcUrl: 'jdbc:dm://127.0.0.1:5236/ERPDMO',
      username: 'SYSDBA',
      status: 'ACTIVE',
    }));
  });
}
