import type { Page, Route } from '@playwright/test';

type IngestionTaskState = {
  task: Record<string, unknown>;
  latestExecution: Record<string, unknown> | null;
  executePollCount: number;
  executionSubmitted: boolean;
};

type ModelingRunSummary = {
  present: boolean;
  invocationId: string;
  generatedAt: string;
  command: string;
  status: string;
  total: number;
  success: number;
  failed: number;
  skipped: number;
  failures: Array<Record<string, unknown>>;
  dagRunId?: string;
  dagId?: string;
  testDetails?: Array<Record<string, unknown>>;
};

type ModelingState = {
  latestRun: ModelingRunSummary;
  dagRuns: Array<Record<string, unknown>>;
  logContent: string;
  runSequence: number;
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

function nowIso(): string {
  return new Date().toISOString();
}

async function readJsonBody(route: Route): Promise<Record<string, unknown>> {
  const raw = route.request().postData() || '{}';
  return JSON.parse(raw) as Record<string, unknown>;
}

function buildModelingSummary(
  operation: 'compile' | 'test' | 'build',
  selector: string,
  sequence: number,
): ModelingRunSummary {
  const base: ModelingRunSummary = {
    present: true,
    invocationId: `${operation}-invocation-${sequence}`,
    generatedAt: nowIso(),
    command: `dbt ${operation} --select ${selector}`,
    status: 'SUCCESS',
    total: 21,
    success: 21,
    failed: 0,
    skipped: 0,
    failures: [],
    dagRunId: `${operation}-dag-run-${sequence}`,
    dagId: 'dwh_project_management_dbt_manual',
  };
  if (operation === 'test') {
    base.testDetails = [
      {
        uniqueId: 'test.dts.not_null_biz_ads_delay_reason_trend_delay_reason_category',
        name: 'not_null_biz_ads_delay_reason_trend_delay_reason_category',
        testType: 'not_null',
        testedModel: 'biz_ads_delay_reason_trend',
        testedColumn: 'delay_reason_category',
        status: 'PASS',
        executionTime: 0.2,
        failuresCount: 0,
        message: '',
      },
    ];
  }
  return base;
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

export async function installPlatformSqlModelingMocks(page: Page): Promise<void> {
  await installPlatformEltShellMocks(page);

  const selector = 'tag:project-management';
  const state: ModelingState = {
    latestRun: {
      present: true,
      invocationId: 'build-invocation-baseline',
      generatedAt: '2026-03-22T12:00:00Z',
      command: 'dbt build --select +tag:project-management',
      status: 'SUCCESS',
      total: 21,
      success: 21,
      failed: 0,
      skipped: 0,
      failures: [],
      dagId: 'dwh_project_management_dbt_manual',
      dagRunId: 'build-dag-run-baseline',
    },
    dagRuns: [],
    logContent: 'dbt build finished successfully',
    runSequence: 1,
  };

  const modelingPlan = {
    id: 'plan-prjtest1',
    name: 'prjtest1',
    domain: 'project-management',
    owner: 'opadmin',
    status: 'ACTIVE',
  };

  const sqlModel = {
    id: 'model-biz-ads-delay-reason-trend',
    planId: 'plan-prjtest1',
    planName: 'prjtest1',
    name: 'biz_ads_delay_reason_trend',
    layer: 'ADS',
    sourceDataSourceId: 'pg-lake',
    sourceDataSourceName: 'pg-lake',
    sourceSystem: 'admin-data-lake',
    dagSelector: selector,
    tags: 'project-management,biz,project-cockpit,ads',
    materialized: 'table',
    schemaName: 'public',
    description: '项目延误原因趋势',
    sql: 'select 1 as project_id',
    enabled: true,
    modelPath: 'models/ads/prjtest1/biz_ads_delay_reason_trend.sql',
    status: 'PUBLISHED',
    metricCount: 0,
    dimensionCount: 0,
  };

  await page.route('**/etl/dbt/config', async (route) => {
    await json(route, ok({
      enabled: true,
      config: {
        enabled: true,
        projectDir: '/opt/dbt',
        profilesDir: '/root/.dbt',
        profileName: 'dts',
        targetName: 'dev',
        targetDataSourceId: 'pg-lake',
        database: 'biadmin',
        schema: 'public',
        vars: {},
      },
      profileStatus: {
        generated: true,
        message: 'Profiles 已生成',
      },
      workspaceStatus: {
        ok: true,
        message: '工作区可用',
      },
      target: {
        id: 'pg-lake',
        name: 'pg-lake',
        type: 'POSTGRESQL',
      },
    }));
  });

  await page.route('**/etl/dbt/sync/status**', async (route) => {
    await json(route, ok({
      manifest: { synced: true, message: '已同步' },
      runResults: { synced: true, message: '已同步' },
      stats: {
        lastSyncAt: nowIso(),
        datasetsCreated: 3,
        datasetsUpdated: 2,
      },
      latestRun: state.latestRun,
    }));
  });

  await page.route('**/etl/dbt/runs**', async (route) => {
    await json(route, ok({
      dag_runs: state.dagRuns,
    }));
  });

  await page.route('**/etl/airflow/jobs/dwh_project_management_dbt_manual/runs**', async (route) => {
    await json(route, ok({
      dag_runs: state.dagRuns,
    }));
  });

  await page.route('**/etl/dbt/dag/ready**', async (route) => {
    await json(route, ok({ ready: true, message: 'DAG ready' }));
  });

  await page.route('**/etl/dbt/quality-gate/check', async (route) => {
    await json(route, ok({ selector, blocking: false, warning: false, blockers: [], warnings: [] }));
  });

  await page.route('**/etl/dbt/release-gate/check', async (route) => {
    await json(route, ok({
      selector,
      blocking: false,
      warning: false,
      blockers: [],
      warnings: [],
      buildEvidence: {
        invocationId: 'quality-evidence-001',
        command: `dbt test --select ${selector}`,
        status: 'SUCCESS',
        generatedAt: nowIso(),
      },
    }));
  });

  await page.route('**/etl/dbt/compile', async (route) => {
    state.runSequence += 1;
    const summary = buildModelingSummary('compile', selector, state.runSequence);
    state.latestRun = summary;
    state.dagRuns = [
      {
        dag_id: summary.dagId,
        dag_run_id: summary.dagRunId,
        state: 'success',
        execution_date: summary.generatedAt,
        start_date: summary.generatedAt,
        end_date: summary.generatedAt,
        conf: {
          models: selector,
          target: 'dev',
          operation: 'compile',
        },
      },
    ];
    await json(route, ok({
      dag_id: summary.dagId,
      dag_run_id: summary.dagRunId,
    }));
  });

  await page.route('**/etl/dbt/test', async (route) => {
    state.runSequence += 1;
    const summary = buildModelingSummary('test', selector, state.runSequence);
    state.latestRun = summary;
    state.dagRuns = [
      {
        dag_id: summary.dagId,
        dag_run_id: summary.dagRunId,
        state: 'success',
        execution_date: summary.generatedAt,
        start_date: summary.generatedAt,
        end_date: summary.generatedAt,
        conf: {
          models: selector,
          target: 'dev',
          operation: 'test',
        },
      },
    ];
    await json(route, ok({
      dag_id: summary.dagId,
      dag_run_id: summary.dagRunId,
    }));
  });

  await page.route('**/etl/dbt/run', async (route) => {
    const body = await readJsonBody(route);
    state.runSequence += 1;
    const summary = buildModelingSummary('build', selector, state.runSequence);
    state.latestRun = {
      ...summary,
      command: `dbt build --select ${String(body.models || '+tag:project-management')}`,
    };
    state.dagRuns = [
      {
        dag_id: summary.dagId,
        dag_run_id: summary.dagRunId,
        state: 'success',
        execution_date: summary.generatedAt,
        start_date: summary.generatedAt,
        end_date: summary.generatedAt,
        conf: {
          models: String(body.models || '+tag:project-management'),
          target: 'dev',
          operation: 'build',
        },
      },
    ];
    state.logContent = 'dbt build finished successfully\ncreated relation public.biz_ads_delay_reason_trend';
    await json(route, ok({
      dag_id: summary.dagId,
      dag_run_id: summary.dagRunId,
    }));
  });

  await page.route('**/etl/dbt/runs/*/logs**', async (route) => {
    await json(route, ok({ log: state.logContent }));
  });

  await page.route('**/modeling/sql-models', async (route) => {
    await json(route, ok([sqlModel]));
  });

  await page.route('**/modeling/sql-models/model-biz-ads-delay-reason-trend/columns', async (route) => {
    await json(route, ok([
      { name: 'program_id', dataType: 'string', comment: '项目群ID', status: 'NORMAL' },
      { name: 'delay_reason_category', dataType: 'string', comment: '延期原因分类', status: 'NORMAL' },
    ]));
  });

  await page.route('**/modeling/sql-models/model-biz-ads-delay-reason-trend/contract-impact', async (route) => {
    await json(route, ok({
      modelId: 'model-biz-ads-delay-reason-trend',
      impactedReportCount: 1,
      fieldCount: 2,
      metricCount: 0,
      dimensionCount: 0,
      impactedReports: [
        {
          id: 'report-1',
          title: '延期原因趋势看板',
        },
      ],
    }));
  });

  await page.route('**/modeling/plans**', async (route) => {
    await json(route, ok([modelingPlan]));
  });

  await page.route('**/infra/data-sources', async (route) => {
    await json(route, ok([
      {
        id: 'pg-lake',
        name: 'pg-lake',
        type: 'POSTGRESQL',
        status: 'ACTIVE',
      },
    ]));
  });

  await page.route('**/modeling/template-layers**', async (route) => {
    await json(route, ok([
      { layer: 'ODS', name: 'ODS', description: '操作数据层' },
      { layer: 'DWD', name: 'DWD', description: '明细数据层' },
      { layer: 'DWS', name: 'DWS', description: '汇总数据层' },
      { layer: 'ADS', name: 'ADS', description: '应用数据层' },
    ]));
  });

  await page.route('**/modeling/sql-models/dbt/sources**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/modeling/sql-models/dbt/refs**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/topic-bindings/status**', async (route) => {
    await json(route, ok({
      selector,
      rows: [
        {
          templateCode: 'project-management',
          templateName: '项目管理',
          entityCode: 'project_subject_domain',
          entityName: '项目主体域',
          required: true,
          bound: true,
          boundSchemaName: 'public',
          boundTableName: 'ods_prj_prjtest2000',
        },
      ],
      missingRequired: [],
    }));
  });
}
