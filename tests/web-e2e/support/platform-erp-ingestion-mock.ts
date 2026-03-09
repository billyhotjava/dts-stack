import type { Page, Route } from '@playwright/test';

type JdbcDriver = {
  id: string;
  fileName: string;
  filePath: string;
  driverClass: string;
  version: string;
  jdkSpec: string;
};

type InfraDataSource = {
  id: string;
  name: string;
  type: string;
  jdbcUrl?: string;
  username?: string;
  description?: string;
  props?: Record<string, unknown>;
  createdAt?: string;
  lastUpdatedAt?: string;
  lastVerifiedAt?: string;
  status?: string;
  hasSecrets?: boolean;
  engineVersion?: string;
  driverVersion?: string;
};

type SyncPipeline = {
  id: string;
  sourceId: string;
  integration: 'JDBC';
  name: string;
  source: string;
  schedule: string;
  lastRun?: string;
  status?: string;
  tablesFound?: number;
  autoEnabled?: boolean;
  logLines?: string[];
};

type SyncRun = {
  id: string;
  integration: 'JDBC';
  status: string;
  startedAt: string;
  finishedAt: string;
  tablesDiscovered: number;
  tablesCreated: number;
  columnsImported: number;
  datasetsCreated: number;
  datasetsUpdated: number;
  datasetsRemoved: number;
  datasetsMarkedStale: number;
  datasetsPurged: number;
  logLines: string[];
};

type TableSummary = {
  fqn: string;
  name: string;
  service: string;
  database: string;
  schema?: string;
  description: string;
  columnCount: number;
};

type TableDetailEntity = {
  enabled: boolean;
  found: boolean;
  entity: {
    columns: Array<{
      name: string;
      dataType: string;
      description?: string;
      status?: string;
    }>;
  };
};

type MockState = {
  drivers: JdbcDriver[];
  dataSources: InfraDataSource[];
  syncTriggered: boolean;
  syncRunAt: string | null;
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

function toSlug(value: string): string {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function buildMenuTree() {
  return [
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

function buildDrivers(): JdbcDriver[] {
  return [
    {
      id: 'dm8-driver',
      fileName: 'DmJdbcDriver18.jar',
      filePath: '/opt/dts/vendor/DmJdbcDriver18.jar',
      driverClass: 'dm.jdbc.driver.DmDriver',
      version: '8.1.2.192',
      jdkSpec: '17',
    },
  ];
}

function buildPipelines(state: MockState): SyncPipeline[] {
  return state.dataSources.map((source) => ({
    id: `${source.id}-metadata`,
    sourceId: source.id,
    integration: 'JDBC',
    name: `${source.name} 元数据同步`,
    source: source.name,
    schedule: 'manual',
    lastRun: state.syncRunAt || undefined,
    status: state.syncTriggered ? 'SUCCESS' : 'IDLE',
    tablesFound: state.syncTriggered ? 2 : 0,
    autoEnabled: false,
    logLines: state.syncTriggered ? ['已发现 2 张 ERP 表', '同步完成'] : ['等待手动触发'],
  }));
}

function buildRuns(state: MockState): SyncRun[] {
  if (!state.syncTriggered || !state.syncRunAt || state.dataSources.length === 0) {
    return [];
  }

  return [
    {
      id: 'erp-demo-run-1',
      integration: 'JDBC',
      status: 'SUCCESS',
      startedAt: state.syncRunAt,
      finishedAt: state.syncRunAt,
      tablesDiscovered: 2,
      tablesCreated: 2,
      columnsImported: 5,
      datasetsCreated: 2,
      datasetsUpdated: 0,
      datasetsRemoved: 0,
      datasetsMarkedStale: 0,
      datasetsPurged: 0,
      logLines: ['连接达梦 ERP 成功', '发现 CUSTOMER / ORDERS', '元数据同步完成'],
    },
  ];
}

function buildTables(state: MockState): TableSummary[] {
  if (!state.syncTriggered || state.dataSources.length === 0) {
    return [];
  }

  const sourceName = state.dataSources[0]?.name || 'ERP Demo DM';

  return [
    {
      fqn: 'ERPDMO.CUSTOMER',
      name: 'CUSTOMER',
      service: sourceName,
      database: 'ERPDMO',
      description: 'ERP 客户主数据',
      columnCount: 2,
    },
    {
      fqn: 'ERPDMO.ORDERS',
      name: 'ORDERS',
      service: sourceName,
      database: 'ERPDMO',
      description: 'ERP 订单事实表',
      columnCount: 3,
    },
  ];
}

function buildTableDetail(fqn: string): TableDetailEntity {
  if (fqn === 'ERPDMO.ORDERS') {
    return {
      enabled: true,
      found: true,
      entity: {
        columns: [
          { name: 'ORDER_ID', dataType: 'BIGINT', description: '订单主键', status: 'ACTIVE' },
          { name: 'CUSTOMER_ID', dataType: 'BIGINT', description: '客户主键', status: 'ACTIVE' },
          { name: 'ORDER_AMOUNT', dataType: 'DECIMAL(18,2)', description: '订单金额', status: 'ACTIVE' },
        ],
      },
    };
  }

  return {
    enabled: true,
    found: true,
    entity: {
      columns: [
        { name: 'CUSTOMER_ID', dataType: 'BIGINT', description: '客户主键', status: 'ACTIVE' },
        { name: 'CUSTOMER_NAME', dataType: 'VARCHAR(255)', description: '客户名称', status: 'ACTIVE' },
      ],
    },
  };
}

function buildDatasource(payload: Record<string, unknown>, state: MockState): InfraDataSource {
  const now = nowIso();
  const name = String(payload.name || '').trim() || 'ERP Demo DM';
  const driverVersion =
    String((payload.props as Record<string, unknown> | undefined)?.driverVersion || '').trim() ||
    state.drivers[0]?.fileName;

  return {
    id: toSlug(name) || 'erp-demo-dm',
    name,
    type: String(payload.type || 'dm').trim() || 'dm',
    jdbcUrl: String(payload.jdbcUrl || '').trim() || undefined,
    username: String(payload.username || '').trim() || undefined,
    description: String(payload.description || '').trim() || undefined,
    props: (payload.props as Record<string, unknown> | undefined) || undefined,
    createdAt: now,
    lastUpdatedAt: now,
    lastVerifiedAt: now,
    status: 'ONLINE',
    hasSecrets: true,
    engineVersion: 'DM 8',
    driverVersion,
  };
}

export async function installPlatformErpIngestionMocks(page: Page): Promise<void> {
  const state: MockState = {
    drivers: buildDrivers(),
    dataSources: [],
    syncTriggered: false,
    syncRunAt: null,
  };

  await page.route('**/api/menu/tree', async (route) => {
    await json(route, ok(buildMenuTree()));
  });

  await page.route('**/api/menu', async (route) => {
    await json(route, ok(buildMenuTree()));
  });

  await page.route('**/api/app-packs/pages**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/app-packs**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/workbench/overview', async (route) => {
    await json(route, ok({}));
  });

  await page.route('**/api/workbench/leader-overview?*', async (route) => {
    await json(route, ok({}));
  });

  await page.route('**/api/ai/agent/sessions?*', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/infra/jdbc-drivers**', async (route) => {
    await json(route, ok(state.drivers));
  });

  await page.route('**/api/infra/data-sources/*/test', async (route) => {
    await json(
      route,
      ok({
        success: true,
        message: '达梦 ERP 连通测试成功',
        elapsedMillis: 42,
        engineVersion: 'DM 8',
        driverVersion: state.drivers[0]?.version,
      }),
    );
  });

  await page.route('**/api/infra/data-sources/*', async (route) => {
    const { pathname } = new URL(route.request().url());
    const id = pathname.split('/').filter(Boolean).at(-1) || '';
    if (route.request().method() === 'DELETE') {
      state.dataSources = state.dataSources.filter((item) => item.id !== id);
      state.syncTriggered = false;
      state.syncRunAt = null;
      await json(route, ok(true));
      return;
    }
    const matched = state.dataSources.find((item) => item.id === id);
    await json(route, ok(matched || null));
  });

  await page.route('**/api/infra/data-sources**', async (route) => {
    if (route.request().method() === 'POST') {
      const payload = await readJsonBody(route);
      const datasource = buildDatasource(payload, state);
      state.dataSources = [...state.dataSources.filter((item) => item.id !== datasource.id), datasource];
      state.syncTriggered = false;
      state.syncRunAt = null;
      await json(route, ok(datasource));
      return;
    }

    await json(route, ok(state.dataSources));
  });

  await page.route('**/api/catalog/sync/runs/*/diagnostics**', async (route) => {
    const [latest] = buildRuns(state);
    await json(route, ok(latest || null));
  });

  await page.route('**/api/catalog/sync/jdbc/*/run', async (route) => {
    state.syncTriggered = true;
    state.syncRunAt = nowIso();
    await json(route, ok({ triggered: true, sourceId: buildPipelines(state)[0]?.sourceId || '' }));
  });

  await page.route('**/api/catalog/sync/config', async (route) => {
    if (route.request().method() === 'POST') {
      const payload = await readJsonBody(route);
      await json(route, ok({ autoSyncEnabled: Boolean(payload.autoSyncEnabled), autoSyncCron: payload.autoSyncCron || '0 */6 * * *' }));
      return;
    }
    await json(
      route,
      ok({
        autoSyncEnabled: false,
        autoSyncCron: '0 */6 * * *',
        cronRuntimeEditable: true,
        message: '测试环境使用手动触发元数据同步',
      }),
    );
  });

  await page.route('**/api/catalog/sync/status', async (route) => {
    await json(
      route,
      ok({
        primary: { inProgress: false },
        jdbc: { inProgress: false },
      }),
    );
  });

  await page.route('**/api/catalog/sync/pipelines', async (route) => {
    await json(route, ok(buildPipelines(state)));
  });

  await page.route('**/api/catalog/sync/runs**', async (route) => {
    await json(route, ok(buildRuns(state)));
  });

  await page.route('**/api/catalog/schema-drift**', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/catalog/metadata/tables/detail**', async (route) => {
    const fqn = new URL(route.request().url()).searchParams.get('fqn') || 'ERPDMO.CUSTOMER';
    await json(route, ok(buildTableDetail(fqn)));
  });

  await page.route('**/api/catalog/metadata/tables**', async (route) => {
    await json(route, ok({ items: buildTables(state) }));
  });
}
