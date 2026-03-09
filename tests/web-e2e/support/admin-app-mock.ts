import type { Page, Route } from '@playwright/test';

type ServiceKey = 'platform' | 'addax' | 'airflow' | 'openmetadata' | 'dbt';
type PackStatus = 'available' | 'installed' | 'disabled';

type IntegrationSettings = {
  settings: Record<string, unknown>;
};

type PackInfo = {
  packId: string;
  packType: 'APPPACK' | 'VAS';
  parentPackId?: string;
  displayName: string;
  description?: string;
  industry?: string;
  version: string;
  publisher?: string;
  installStatus: PackStatus;
  installedAt?: string;
};

type UpdatePayload = {
  packId: string;
  packType: string;
  displayName: string;
  version: string;
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

function buildInitialSettings(): Record<ServiceKey, IntegrationSettings> {
  return {
    platform: {
      settings: {
        enabled: true,
        catalogSyncOnDataSource: true,
      },
    },
    addax: {
      settings: {
        enabled: true,
        jobDir: '/opt/airflow/dags',
        image: 'quay.io/wgzhao/addax:6.0.8',
      },
    },
    airflow: {
      settings: {
        enabled: true,
        baseUrl: 'http://dts-airflow-web:8080',
        apiPath: '/api/v1',
        dagsDir: '/opt/airflow/dags',
        username: 'airflow',
        password: 'airflow',
        dagId: 'dbt_load',
      },
    },
    openmetadata: {
      settings: {
        enabled: true,
        baseUrl: 'http://openmetadata:8585',
        apiPath: '/api/v1',
        authToken: 'Bearer demo-token',
        tableFields: 'columns,owner,tags,domain',
        sourceServiceName: 'source_service',
        sourceServiceType: 'Postgres',
        destinationServiceName: 'destination_service',
        destinationServiceType: 'Postgres',
        sourceDatabase: 'source_db',
        sourceSchema: 'public',
        destinationDatabase: 'ods',
        destinationSchema: 'public',
        ingestionEnabled: true,
        ingestionPrefix: 'dts_ingest',
        ingestionSchedule: '0 * * * *',
      },
    },
    dbt: {
      settings: {
        enabled: true,
        baseUrl: 'http://dbt-service:8080',
        apiPath: '/api/v1',
        username: 'dbt',
        password: 'dbt-password',
        token: 'dbt-token',
      },
    },
  };
}

function buildInitialPacks(): PackInfo[] {
  return [
    {
      packId: 'crm-insight-pack',
      packType: 'APPPACK',
      displayName: 'CRM Insight Pack',
      description: '客户洞察应用包，包含销售转化与客户分层模板。',
      industry: '制造',
      version: '2.4.1',
      publisher: 'DTS',
      installStatus: 'available',
    },
    {
      packId: 'finance-ops-pack',
      packType: 'APPPACK',
      displayName: 'Finance Ops Pack',
      description: '经营分析与预算追踪应用包。',
      industry: '集团',
      version: '2.4.1',
      publisher: 'DTS',
      installStatus: 'installed',
      installedAt: '2026-03-06T09:30:00Z',
    },
    {
      packId: 'erp-vas-assistant',
      packType: 'VAS',
      parentPackId: 'crm-insight-pack',
      displayName: 'ERP VAS Assistant',
      description: 'ERP 对接增强服务。',
      industry: '制造',
      version: '1.0.0',
      publisher: 'DTS',
      installStatus: 'disabled',
      installedAt: '2026-03-05T08:00:00Z',
    },
  ];
}

function withFilter(packs: PackInfo[], type: string | null): PackInfo[] {
  if (!type) {
    return packs;
  }
  return packs.filter((pack) => pack.packType === type);
}

function findPack(packs: PackInfo[], packId: string): PackInfo {
  const pack = packs.find((candidate) => candidate.packId === packId);
  if (!pack) {
    throw new Error(`unknown pack: ${packId}`);
  }
  return pack;
}

export async function installAdminAppMocks(page: Page): Promise<void> {
  const settings = buildInitialSettings();
  const packs = buildInitialPacks();

  await page.route('**/api/menu', async (route) => {
    await json(route, ok([]));
  });

  await page.route('**/api/admin/whoami', async (route) => {
    await json(
      route,
      ok({
        allowed: true,
        role: 'ROLE_SYS_ADMIN',
        username: 'sysadmin',
        email: 'sysadmin@example.com',
      }),
    );
  });

  await page.route('**/api/admin/portal/menus', async (route) => {
    await json(route, ok({ menus: [], allMenus: [] }));
  });

  await page.route('**/api/admin/infra/settings/*/test', async (route) => {
    const service = route.request().url().split('/').filter(Boolean).at(-2) as ServiceKey;
    const payload = await readJsonBody(route);
    await json(
      route,
      ok({
        success: true,
        message: `${service} 连接成功`,
        status: 200,
        body: JSON.stringify(payload),
      }),
    );
  });

  await page.route('**/api/admin/infra/settings/*', async (route) => {
    const service = route.request().url().split('/').filter(Boolean).at(-1) as ServiceKey;
    if (route.request().method() === 'POST') {
      const payload = await readJsonBody(route);
      settings[service] = { settings: { ...payload } };
      await json(route, ok(settings[service]));
      return;
    }
    await json(route, ok(settings[service]));
  });

  await page.route('**/api/admin/packs/install', async (route) => {
    const payload = (await readJsonBody(route)) as unknown as UpdatePayload;
    const pack = findPack(packs, payload.packId);
    pack.installStatus = 'installed';
    pack.installedAt = nowIso();
    await json(route, ok(pack));
  });

  await page.route('**/api/admin/packs/*/status', async (route) => {
    const parts = new URL(route.request().url()).pathname.split('/').filter(Boolean);
    const packId = parts.at(-2) || '';
    const payload = await readJsonBody(route);
    const nextStatus = String(payload.status || '').trim() as PackStatus;
    const pack = findPack(packs, packId);
    pack.installStatus = nextStatus;
    if (nextStatus === 'installed' && !pack.installedAt) {
      pack.installedAt = nowIso();
    }
    await json(route, ok(true));
  });

  await page.route('**/api/admin/packs/*/uninstall', async (route) => {
    const parts = new URL(route.request().url()).pathname.split('/').filter(Boolean);
    const packId = parts.at(-2) || '';
    const pack = findPack(packs, packId);
    pack.installStatus = 'available';
    pack.installedAt = undefined;
    await json(route, ok(true));
  });

  await page.route('**/api/admin/packs**', async (route) => {
    const url = new URL(route.request().url());
    const type = url.searchParams.get('type');
    await json(route, ok(withFilter(packs, type)));
  });
}
