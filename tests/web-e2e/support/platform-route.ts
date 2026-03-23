function normalizeRoute(route: string): string {
  const trimmed = String(route || '').trim();
  if (!trimmed) {
    return '/';
  }
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
}

function platformRouterHistory(): 'browser' | 'hash' {
  const raw = String(process.env.DTS_PLATFORM_ROUTER_HISTORY || process.env.VITE_APP_ROUTER_HISTORY || 'hash')
    .trim()
    .toLowerCase();
  return raw === 'browser' ? 'browser' : 'hash';
}

export function buildPlatformRouteUrl(baseUrl: string, route: string): string {
  const normalizedRoute = normalizeRoute(route);

  if (platformRouterHistory() === 'hash') {
    const url = new URL(baseUrl);
    url.hash = normalizedRoute;
    return url.toString();
  }

  return new URL(normalizedRoute.slice(1), baseUrl).toString();
}
