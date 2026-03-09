import { resolveAuthUrls, testServerOrigin, useTestServer } from './storage-state';

export type ApiJson = Record<string, unknown>;

export class WebE2EApiError extends Error {
  readonly status: number;
  readonly bodyText: string;

  constructor(message: string, status: number, bodyText: string) {
    super(message);
    this.name = 'WebE2EApiError';
    this.status = status;
    this.bodyText = bodyText;
  }
}

function normalizeBaseUrl(url: string): string {
  return url.endsWith('/') ? url.slice(0, -1) : url;
}

export function resolveApiBaseUrl(): string {
  const explicit = process.env.DTS_WEB_E2E_API_BASE_URL?.trim();
  if (explicit) {
    return normalizeBaseUrl(explicit);
  }

  if (useTestServer()) {
    return normalizeBaseUrl(testServerOrigin());
  }

  const urls = resolveAuthUrls();
  const fallback = urls.authGateway || urls.platform || urls.analytics || urls.admin;
  if (!fallback) {
    throw new Error(
      'No API base URL configured. Set DTS_WEB_E2E_API_BASE_URL or enable DTS_WEB_E2E_USE_TEST_SERVER.',
    );
  }
  return normalizeBaseUrl(fallback);
}

async function requestJson(method: 'GET' | 'POST', pathname: string, payload?: ApiJson): Promise<ApiJson> {
  const baseUrl = resolveApiBaseUrl();
  const response = await fetch(`${baseUrl}${pathname}`, {
    method,
    headers: {
      accept: 'application/json',
      ...(payload ? { 'content-type': 'application/json' } : {}),
    },
    body: payload ? JSON.stringify(payload) : undefined,
  });

  const text = await response.text();
  let json: ApiJson = {};
  try {
    json = text ? (JSON.parse(text) as ApiJson) : {};
  } catch {
    json = {};
  }

  if (!response.ok) {
    throw new WebE2EApiError(`API ${method} ${pathname} failed with ${response.status}`, response.status, text);
  }
  return json;
}

export async function getJson(pathname: string): Promise<ApiJson> {
  return requestJson('GET', pathname);
}

export async function postJson(pathname: string, payload: ApiJson): Promise<ApiJson> {
  return requestJson('POST', pathname, payload);
}
