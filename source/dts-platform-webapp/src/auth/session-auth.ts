import { buildLoginRedirectHref, currentRoutePath as resolveCurrentRoutePath } from "@dts-session-core/route";
import { resolveLoginHref } from '@/routes/constants';
import userStore from '@/store/userStore';
import { GLOBAL_CONFIG } from '@/global-config';

// ─── Types ───────────────────────────────────────────────────────────────────

export type RefreshResult = {
	accessToken: string;
	refreshToken: string;
	adminAccessToken?: string;
	adminRefreshToken?: string;
	adminAccessTokenExpiresAt?: string;
	adminRefreshTokenExpiresAt?: string;
} | null;

// ─── Observability ───────────────────────────────────────────────────────────

const SESSION_DOMAIN = 'platform';
const LOG_PREFIX = `[session:${SESSION_DOMAIN}]`;

// ─── Single-flight token refresh with cross-tab sync ─────────────────────────
//
// Problem: When multiple tabs share the same refresh token (via localStorage/Zustand),
// Keycloak's one-time-use refresh token causes a race condition:
//   Tab A refreshes with R1 → gets R2 (R1 is now invalid)
//   Tab B also tries R1 → 401 (R1 already used)
//   Tab B enters a 401 retry storm → CPU spike → session death
//
// Solution: Use localStorage `storage` event as a cross-tab broadcast channel.
// When one tab successfully refreshes, it writes the new tokens to localStorage.
// Other tabs detect the `storage` event, read the new tokens, and skip their own refresh.

let _refreshPromise: Promise<RefreshResult> | null = null;

/** Cooldown after a refresh failure — prevents 401 retry storms. */
let _refreshFailedAt = 0;
const REFRESH_COOLDOWN_MS = 5_000;

/** Cross-tab sync key — written after successful refresh, read by other tabs via storage event. */
const CROSS_TAB_REFRESH_KEY = 'dts:session:cross-tab-refresh';

// Listen for token updates from other tabs
if (typeof window !== 'undefined') {
	window.addEventListener('storage', (event) => {
		if (event.key !== CROSS_TAB_REFRESH_KEY || !event.newValue) return;
		try {
			const data = JSON.parse(event.newValue);
			if (data?.accessToken) {
				console.debug(LOG_PREFIX, 'refresh: received cross-tab token update');
				const existing = userStore.getState().userToken;
				userStore.getState().actions.setUserToken({
					accessToken: data.accessToken,
					refreshToken: data.refreshToken || existing?.refreshToken,
					adminAccessToken: data.adminAccessToken || existing?.adminAccessToken,
					adminRefreshToken: data.adminRefreshToken || existing?.adminRefreshToken,
					adminAccessTokenExpiresAt: data.adminAccessTokenExpiresAt || existing?.adminAccessTokenExpiresAt,
					adminRefreshTokenExpiresAt: data.adminRefreshTokenExpiresAt || existing?.adminRefreshTokenExpiresAt,
				});
				// Reset cooldown — we have fresh tokens from another tab
				_refreshFailedAt = 0;
			}
		} catch {
			// Ignore parse errors
		}
	});
}

/**
 * Broadcast new tokens to other tabs via localStorage.
 */
function broadcastTokensToOtherTabs(result: RefreshResult): void {
	if (!result) return;
	try {
		localStorage.setItem(CROSS_TAB_REFRESH_KEY, JSON.stringify({
			accessToken: result.accessToken,
			refreshToken: result.refreshToken,
			adminAccessToken: result.adminAccessToken,
			adminRefreshToken: result.adminRefreshToken,
			adminAccessTokenExpiresAt: result.adminAccessTokenExpiresAt,
			adminRefreshTokenExpiresAt: result.adminRefreshTokenExpiresAt,
			ts: Date.now(),
		}));
	} catch {
		// localStorage might be full or unavailable
	}
}

/**
 * 刷新平台 access token。
 *
 * - 如果已有一个 refresh 请求在飞，所有后续调用者复用同一个 Promise
 * - 成功后自动写入 userStore 并广播到其他标签页
 * - 失败后进入 5s 冷却期，防止 401 重试风暴
 * - 使用原生 fetch 而非 axios，避免进入 apiClient 响应拦截器的递归循环
 */
export async function refreshAccessToken(): Promise<RefreshResult> {
	// Cooldown: if refresh recently failed, don't retry immediately
	if (_refreshFailedAt > 0 && Date.now() - _refreshFailedAt < REFRESH_COOLDOWN_MS) {
		console.debug(LOG_PREFIX, 'refresh: skipped (cooldown active)');
		return null;
	}

	const { userToken } = userStore.getState();
	const refreshToken = String(userToken?.refreshToken || '').trim();
	if (!refreshToken) return null;

	// Single-flight: 复用已有的刷新请求
	if (_refreshPromise) {
		console.debug(LOG_PREFIX, 'refresh: joined in-flight request');
		return _refreshPromise;
	}

	console.debug(LOG_PREFIX, 'refresh: attempt');
	_refreshPromise = (async (): Promise<RefreshResult> => {
		try {
			// Before calling the server, check if another tab just refreshed
			// (the `storage` event might have updated userStore with fresh tokens)
			const latestToken = userStore.getState().userToken;
			const latestRefresh = String(latestToken?.refreshToken || '').trim();
			const actualRefreshToken = latestRefresh || refreshToken;

			const apiBase = GLOBAL_CONFIG.apiBaseUrl || '/api';
			const resp = await fetch(`${apiBase}/keycloak/auth/refresh`, {
				method: 'POST',
				credentials: 'include',
				headers: { 'content-type': 'application/json', accept: 'application/json' },
				body: JSON.stringify({ refreshToken: actualRefreshToken }),
			});
			if (!resp.ok) {
				console.warn(LOG_PREFIX, 'refresh: failed', { status: resp.status });
				_refreshFailedAt = Date.now();
				return null;
			}

			const body = await resp.json().catch(() => null);
			const data = body?.data ?? body?.result ?? body?.payload ?? body;

			const nextAccess = String(data?.accessToken || data?.access_token || data?.token || '').trim();
			if (!nextAccess) {
				_refreshFailedAt = Date.now();
				return null;
			}

			const nextRefresh = String(data?.refreshToken || data?.refresh_token || '').trim();
			const adminAccessToken = String(data?.adminAccessToken || '').trim() || undefined;
			const adminRefreshToken = String(data?.adminRefreshToken || '').trim() || undefined;
			const adminAccessTokenExpiresAt = String(data?.adminAccessTokenExpiresAt || '').trim() || undefined;
			const adminRefreshTokenExpiresAt = String(data?.adminRefreshTokenExpiresAt || '').trim() || undefined;

			const existing = userStore.getState().userToken;
			userStore.getState().actions.setUserToken({
				accessToken: nextAccess,
				refreshToken: nextRefresh || actualRefreshToken,
				adminAccessToken: adminAccessToken || existing?.adminAccessToken,
				adminRefreshToken: adminRefreshToken || existing?.adminRefreshToken,
				adminAccessTokenExpiresAt: adminAccessTokenExpiresAt || existing?.adminAccessTokenExpiresAt,
				adminRefreshTokenExpiresAt: adminRefreshTokenExpiresAt || existing?.adminRefreshTokenExpiresAt,
			});

			_refreshFailedAt = 0; // reset cooldown on success

			const result: RefreshResult = {
				accessToken: nextAccess,
				refreshToken: nextRefresh || actualRefreshToken,
				adminAccessToken,
				adminRefreshToken,
				adminAccessTokenExpiresAt,
				adminRefreshTokenExpiresAt,
			};

			// Broadcast to other tabs so they don't use the stale refresh token
			broadcastTokensToOtherTabs(result);

			console.debug(LOG_PREFIX, 'refresh: success');
			return result;
		} catch (err) {
			console.warn(LOG_PREFIX, 'refresh: error', err instanceof Error ? err.message : err);
			_refreshFailedAt = Date.now();
			return null;
		}
	})().finally(() => {
		_refreshPromise = null;
	});

	return _refreshPromise;
}

// ─── Login redirect ──────────────────────────────────────────────────────────

let _redirecting = false;

/**
 * 获取当前路由路径（hash 路由感知）。
 *
 * hash 模式下 window.location.pathname 始终为 "/"，真实路由在 hash 里。
 */
export function currentRoutePath(): string {
	return resolveCurrentRoutePath(GLOBAL_CONFIG.routerHistory);
}

/**
 * 重定向到登录页，自动携带当前路由作为 ?redirect= 参数。
 *
 * 幂等：同一时刻只触发一次跳转。hash 路由下 window.location.href 不会导致
 * 真正的 page reload，所以模块级变量不会自动重置 —— 需要在登录成功后手动调用
 * resetLoginRedirectFlag()。
 */
export function redirectToLoginWithReturn(): void {
	if (_redirecting) return;
	_redirecting = true;
	const returnPath = currentRoutePath();
	const href = buildLoginRedirectHref(resolveLoginHref(), returnPath);
	console.warn(LOG_PREFIX, 'redirect: to login', { returnPath, href });
	window.location.replace(href);
}

/**
 * 登录成功后重置重定向守卫，使后续会话过期可以再次触发重定向。
 */
export function resetLoginRedirectFlag(): void {
	_redirecting = false;
}
