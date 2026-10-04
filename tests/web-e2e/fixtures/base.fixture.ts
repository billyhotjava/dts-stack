import { expect, test } from '@playwright/test';

export { expect, test };

export type AppTargetName = 'admin' | 'platform' | 'analytics' | 'auth-gateway';

export interface ShellTarget {
  name: AppTargetName | 'custom';
  url: string;
}

const TARGET_ENV: Record<AppTargetName, string[]> = {
  admin: ['DTS_ADMIN_URL', 'DTS_ADMIN_WEB_URL'],
  platform: ['DTS_PLATFORM_URL', 'DTS_BASE_URL', 'DTS_PLATFORM_WEB_URL'],
  analytics: ['DTS_ANALYTICS_URL', 'DTS_ANALYTICS_WEB_URL'],
  'auth-gateway': ['DTS_AUTH_GATEWAY_URL'],
};

function firstNonEmptyEnv(keys: string[]): string | null {
  for (const key of keys) {
    const value = process.env[key]?.trim();
    if (value) {
      return value;
    }
  }
  return null;
}

function resolveTargetByName(name: AppTargetName): ShellTarget | null {
  const url = firstNonEmptyEnv(TARGET_ENV[name]);
  if (!url) {
    return null;
  }
  return { name, url };
}

function parseRequestedTarget(raw: string | undefined): AppTargetName | null {
  const value = raw?.trim().toLowerCase();
  if (!value) {
    return null;
  }
  if (value === 'auth' || value === 'auth-gateway') {
    return 'auth-gateway';
  }
  if (value === 'admin' || value === 'platform' || value === 'analytics') {
    return value;
  }
  throw new Error(
    `Unsupported DTS_WEB_E2E_TARGET="${raw}". Expected one of: admin, platform, analytics, auth-gateway.`,
  );
}

export function resolveBaseUrls(): Record<AppTargetName, string | null> {
  return {
    admin: firstNonEmptyEnv(TARGET_ENV.admin),
    platform: firstNonEmptyEnv(TARGET_ENV.platform),
    analytics: firstNonEmptyEnv(TARGET_ENV.analytics),
    'auth-gateway': firstNonEmptyEnv(TARGET_ENV['auth-gateway']),
  };
}

export function requireShellTarget(): ShellTarget {
  const directUrl = process.env.DTS_WEB_E2E_SMOKE_URL?.trim();
  if (directUrl) {
    return { name: 'custom', url: directUrl };
  }

  const requested = parseRequestedTarget(process.env.DTS_WEB_E2E_TARGET);
  if (requested) {
    const target = resolveTargetByName(requested);
    if (!target) {
      throw new Error(
        `DTS_WEB_E2E_TARGET=${requested} but no URL is configured. Set one of: ${TARGET_ENV[requested].join(', ')}.`,
      );
    }
    return target;
  }

  const fallbacks: AppTargetName[] = ['platform', 'analytics', 'admin', 'auth-gateway'];
  for (const name of fallbacks) {
    const target = resolveTargetByName(name);
    if (target) {
      return target;
    }
  }

  throw new Error(
    'No web E2E target configured. Set DTS_WEB_E2E_SMOKE_URL or one of DTS_PLATFORM_URL/DTS_BASE_URL, DTS_ANALYTICS_URL, DTS_ADMIN_URL, DTS_AUTH_GATEWAY_URL.',
  );
}
