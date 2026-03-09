import fsSync from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';

export type AuthApp = 'admin' | 'platform' | 'analytics';
type AuthScope = 'admin' | 'platform';

type StoreRecord = {
  state: {
    userInfo: Record<string, unknown>;
    userToken: Record<string, unknown>;
  };
  version: number;
};

export type AuthUrls = {
  authGateway: string;
  authLogin: string;
  admin: string;
  expert: string;
  platform: string;
  analytics: string;
};

type ScopeConfig = {
  scope: AuthScope;
  username: string;
  password: string;
  token: string;
  requirePasswordLogin: boolean;
  loginUrls: string[];
};

type AuthSession = {
  username: string;
  accessToken: string;
  refreshToken: string;
  userInfo: Record<string, unknown>;
  source: string;
};

const DEFAULT_DEV_SERVER_PORT = 19333;
const STORAGE_STATE_DIR = path.resolve(process.cwd(), 'playwright/.auth');

function truthy(value: string | undefined): boolean {
  const normalized = String(value ?? '').trim().toLowerCase();
  return normalized === '1' || normalized === 'true' || normalized === 'yes' || normalized === 'on';
}

function firstEnv(names: string[], fallback = ''): string {
  for (const name of names) {
    const value = process.env[name]?.trim();
    if (value) {
      return value;
    }
  }
  return fallback;
}

function normalizeAccessToken(token: string): string {
  const value = String(token ?? '').trim();
  if (!value) {
    return '';
  }
  return value.toLowerCase().startsWith('bearer ') ? value.slice(7).trim() : value;
}

function joinUrl(base: string, pathname: string): string {
  return new URL(pathname, base.endsWith('/') ? base : `${base}/`).toString();
}

function pickToken(value: unknown, keys: string[]): string {
  if (!value || typeof value !== 'object') {
    return '';
  }
  for (const key of keys) {
    const candidate = (value as Record<string, unknown>)[key];
    if (typeof candidate === 'string' && candidate.trim()) {
      return candidate.trim();
    }
  }
  return '';
}

function normalizeRoleList(raw: unknown): string[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  const values = new Set<string>();
  for (const item of raw) {
    if (typeof item === 'string' && item.trim()) {
      values.add(item.trim());
    } else if (item && typeof item === 'object') {
      const candidate = (item as Record<string, unknown>).code ?? (item as Record<string, unknown>).name;
      if (typeof candidate === 'string' && candidate.trim()) {
        values.add(candidate.trim());
      }
    }
  }
  return Array.from(values);
}

function defaultUserInfo(scope: AuthScope, username: string): Record<string, unknown> {
  const normalized = username.trim().toLowerCase();
  if (scope === 'admin') {
    return {
      username,
      fullName: '系统管理员',
      roles: normalized === 'authadmin' ? ['ROLE_AUTH_ADMIN'] : normalized === 'auditadmin' ? ['ROLE_SECURITY_AUDITOR'] : ['ROLE_SYS_ADMIN'],
      permissions: [],
      enabled: true,
    };
  }
  return {
    username,
    fullName: '业务运维管理员',
    roles: ['ROLE_OP_ADMIN'],
    permissions: [],
    enabled: true,
  };
}

function mergeUserInfo(scope: AuthScope, username: string, user: unknown): Record<string, unknown> {
  const base = defaultUserInfo(scope, username);
  if (!user || typeof user !== 'object') {
    return base;
  }
  const raw = user as Record<string, unknown>;
  const merged: Record<string, unknown> = {
    ...base,
    ...raw,
  };
  const roles = normalizeRoleList(raw.roles) || normalizeRoleList(raw.authorities);
  if (roles.length > 0) {
    merged.roles = roles;
  }
  if (typeof merged.username !== 'string' || !String(merged.username).trim()) {
    merged.username = username;
  }
  if (typeof merged.fullName !== 'string' || !String(merged.fullName).trim()) {
    merged.fullName = base.fullName;
  }
  if (!Array.isArray(merged.permissions)) {
    merged.permissions = [];
  }
  return merged;
}

function buildStoreRecord(session: AuthSession): StoreRecord {
  return {
    state: {
      userInfo: session.userInfo,
      userToken: {
        accessToken: session.accessToken,
        refreshToken: session.refreshToken,
      },
    },
    version: 0,
  };
}

function buildLocalStorageEntries(app: AuthApp, session: AuthSession): Array<{ name: string; value: string }> {
  if (app === 'admin') {
    return [{ name: 'adminUserStore', value: JSON.stringify(buildStoreRecord(session)) }];
  }

  const record = JSON.stringify(buildStoreRecord(session));
  const now = String(Date.now());
  return [
    { name: 'platformUserStore', value: record },
    { name: 'userStore', value: record },
    { name: 'dts.platform.session.loginTs', value: now },
    { name: 'dts.platform.session.lastActivity', value: now },
  ];
}

function appOrigin(url: string): string {
  return new URL(url).origin;
}

export function storageStatePathFor(app: AuthApp): string {
  return path.join(STORAGE_STATE_DIR, `${app}.json`);
}

export function namedStorageStatePath(name: string): string {
  return path.join(STORAGE_STATE_DIR, `${String(name || '').trim()}.json`);
}

export function useTestServer(): boolean {
  return !['0', 'false', 'no', 'off'].includes(String(process.env.DTS_WEB_E2E_USE_TEST_SERVER ?? '1').trim().toLowerCase());
}

export function testServerOrigin(): string {
  const port = Number(process.env.DTS_WEB_E2E_DEV_SERVER_PORT || DEFAULT_DEV_SERVER_PORT);
  return `http://127.0.0.1:${port}`;
}

export function resolveAuthUrls(): AuthUrls {
  const fallbackOrigin = useTestServer() ? testServerOrigin() : '';
  const authGateway = firstEnv(['DTS_AUTH_GATEWAY_URL'], fallbackOrigin);
  const admin = firstEnv(['DTS_ADMIN_URL'], fallbackOrigin ? `${fallbackOrigin}/admin/` : '');
  const platform = firstEnv(['DTS_PLATFORM_URL', 'DTS_BASE_URL'], fallbackOrigin ? `${fallbackOrigin}/expert/` : '');
  const analytics = firstEnv(['DTS_ANALYTICS_URL'], fallbackOrigin ? `${fallbackOrigin}/analytics/` : '');
  const expert = firstEnv(['DTS_EXPERT_URL'], platform || (fallbackOrigin ? `${fallbackOrigin}/expert/` : ''));
  const authLogin = authGateway ? joinUrl(authGateway, '/auth/login') : '';

  return {
    authGateway,
    authLogin,
    admin,
    expert,
    platform: platform || expert,
    analytics,
  };
}

function resolveScopeConfig(scope: AuthScope, urls: AuthUrls): ScopeConfig {
  if (scope === 'admin') {
    const baseUrl = urls.admin || urls.authGateway;
    return {
      scope,
      username: firstEnv(['DTS_IT_ADMIN_USER', 'DTS_ADMIN_USER', 'ADMIN_USER'], useTestServer() ? 'sysadmin' : ''),
      password: firstEnv(['DTS_IT_ADMIN_PASS', 'DTS_ADMIN_PASS', 'ADMIN_PASS'], useTestServer() ? 'sa' : ''),
      token: firstEnv(['DTS_ADMIN_AUTH_TOKEN', 'DTS_AUTH_TOKEN'], ''),
      requirePasswordLogin: truthy(process.env.DTS_ADMIN_REQUIRE_PASSWORD_LOGIN ?? (useTestServer() ? '1' : '0')),
      loginUrls: baseUrl ? [joinUrl(baseUrl, '/api/keycloak/auth/login')] : [],
    };
  }

  const platformBase = urls.platform || urls.expert || urls.authGateway;
  return {
    scope,
    username: firstEnv(['DTS_IT_PLATFORM_USER', 'DTS_PLATFORM_USER', 'PLATFORM_USER'], useTestServer() ? 'opadmin' : ''),
    password: firstEnv(['DTS_IT_PLATFORM_PASS', 'DTS_PLATFORM_PASS', 'PLATFORM_PASS'], useTestServer() ? 'sa' : ''),
    token: firstEnv(['DTS_PLATFORM_AUTH_TOKEN', 'DTS_AUTH_TOKEN'], ''),
    requirePasswordLogin: truthy(process.env.DTS_PLATFORM_REQUIRE_PASSWORD_LOGIN ?? (useTestServer() ? '1' : '0')),
    loginUrls: Array.from(
      new Set(
        [urls.authGateway, platformBase]
          .filter(Boolean)
          .flatMap((baseUrl) => [joinUrl(baseUrl, '/api/keycloak/auth/login'), joinUrl(baseUrl, '/api/keycloak/auth/platform/login')]),
      ),
    ),
  };
}

async function postJson(
  url: string,
  payload: Record<string, unknown>,
): Promise<{ ok: boolean; status: number; json: Record<string, unknown> | null; text: string }> {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      accept: 'application/json',
      'content-type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
  const text = await response.text();
  let json: Record<string, unknown> | null = null;
  try {
    json = JSON.parse(text) as Record<string, unknown>;
  } catch {
    json = null;
  }
  return { ok: response.ok, status: response.status, json, text };
}

async function loginWithPassword(config: ScopeConfig): Promise<AuthSession> {
  const errors: string[] = [];
  for (const loginUrl of config.loginUrls) {
    try {
      const payload: Record<string, unknown> = {
        username: config.username,
        password: config.password,
      };
      if (useTestServer()) {
        payload.scope = config.scope;
      }
      const response = await postJson(loginUrl, payload);
      const body = (response.json?.data as Record<string, unknown>) || response.json || {};
      const accessToken = normalizeAccessToken(
        pickToken(body, ['accessToken', 'access_token', 'token']) || pickToken(response.json, ['accessToken', 'access_token', 'token']),
      );
      if (response.ok && accessToken) {
        const refreshToken =
          pickToken(body, ['refreshToken', 'refresh_token']) || pickToken(response.json, ['refreshToken', 'refresh_token']);
        const userInfo = mergeUserInfo(config.scope, config.username, body.user ?? response.json?.user);
        return {
          username: config.username,
          accessToken,
          refreshToken,
          userInfo,
          source: `password:${loginUrl}`,
        };
      }
      errors.push(`${loginUrl} -> http=${response.status} body=${response.text.slice(0, 180)}`);
    } catch (error) {
      errors.push(`${loginUrl} -> ${(error as Error).message}`);
    }
  }
  throw new Error(`password login failed for ${config.scope}: ${errors.join('; ') || 'no login endpoint configured'}`);
}

function tokenFallbackSession(config: ScopeConfig): AuthSession {
  const token = normalizeAccessToken(config.token);
  if (!token) {
    throw new Error(
      `${config.scope} auth requires credentials or token. Set ${config.scope === 'admin' ? 'DTS_IT_ADMIN_USER/DTS_IT_ADMIN_PASS or DTS_ADMIN_AUTH_TOKEN' : 'DTS_IT_PLATFORM_USER/DTS_IT_PLATFORM_PASS or DTS_PLATFORM_AUTH_TOKEN'}.`,
    );
  }
  return {
    username: config.username || `${config.scope}-token-user`,
    accessToken: token,
    refreshToken: '',
    userInfo: defaultUserInfo(config.scope, config.username || `${config.scope}-token-user`),
    source: `token:${config.scope}`,
  };
}

async function resolveSession(scope: AuthScope, urls: AuthUrls): Promise<AuthSession> {
  const config = resolveScopeConfig(scope, urls);
  if (config.token && !config.requirePasswordLogin) {
    return tokenFallbackSession(config);
  }

  if (config.username && config.password) {
    try {
      return await loginWithPassword(config);
    } catch (error) {
      if (config.token) {
        return tokenFallbackSession(config);
      }
      throw error;
    }
  }

  return tokenFallbackSession(config);
}

async function writeStateFile(app: AuthApp, session: AuthSession, urls: AuthUrls): Promise<void> {
  const targetUrl = app === 'admin' ? urls.admin : app === 'analytics' ? urls.analytics : urls.expert || urls.platform;
  if (!targetUrl) {
    throw new Error(`missing target URL for ${app}; check DTS_* URL configuration`);
  }

  const storageState = {
    cookies: [],
    origins: [
      {
        origin: appOrigin(targetUrl),
        localStorage: buildLocalStorageEntries(app, session),
      },
    ],
  };
  await fs.writeFile(storageStatePathFor(app), JSON.stringify(storageState, null, 2), 'utf-8');
}

export function writeNamedStorageStateSync(
  name: string,
  app: AuthApp,
  session: {
    username: string;
    accessToken: string;
    refreshToken?: string;
    userInfo?: Record<string, unknown>;
  },
  urls = resolveAuthUrls(),
): string {
  const targetUrl = app === 'admin' ? urls.admin : app === 'analytics' ? urls.analytics : urls.expert || urls.platform;
  if (!targetUrl) {
    throw new Error(`missing target URL for ${app}; check DTS_* URL configuration`);
  }

  const scope: AuthScope = app === 'admin' ? 'admin' : 'platform';
  const normalizedSession: AuthSession = {
    username: session.username,
    accessToken: normalizeAccessToken(session.accessToken),
    refreshToken: String(session.refreshToken || '').trim(),
    userInfo: mergeUserInfo(scope, session.username, session.userInfo),
    source: `named:${name}`,
  };

  const filePath = namedStorageStatePath(name);
  fsSync.mkdirSync(path.dirname(filePath), { recursive: true });
  fsSync.writeFileSync(
    filePath,
    JSON.stringify(
      {
        cookies: [],
        origins: [
          {
            origin: appOrigin(targetUrl),
            localStorage: buildLocalStorageEntries(app, normalizedSession),
          },
        ],
      },
      null,
      2,
    ),
    'utf-8',
  );
  return filePath;
}

export async function ensureStorageStates(): Promise<void> {
  const urls = resolveAuthUrls();
  await fs.mkdir(STORAGE_STATE_DIR, { recursive: true });

  const platformSession = await resolveSession('platform', urls);
  const adminSession = await resolveSession('admin', urls);

  await writeStateFile('platform', platformSession, urls);
  await writeStateFile('analytics', platformSession, urls);
  await writeStateFile('admin', adminSession, urls);
}
