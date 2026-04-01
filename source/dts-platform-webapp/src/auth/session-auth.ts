import { create } from 'zustand';
import { currentRoutePath as resolveCurrentRoutePath } from "@dts-session-core/route";
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
 * Read tokens from the cross-tab broadcast channel (localStorage).
 * Returns non-null only if the stored refresh token differs from the one we used,
 * meaning another tab successfully refreshed.
 *
 * This reads localStorage DIRECTLY — not Zustand in-memory state — because
 * localStorage.setItem() in Tab A is synchronously visible to Tab B via
 * localStorage.getItem(), while the `storage` event (which syncs Zustand) is async.
 */
function readCrossTabTokens(usedRefreshToken: string): RefreshResult {
	try {
		const raw = localStorage.getItem(CROSS_TAB_REFRESH_KEY);
		if (!raw) return null;
		const data = JSON.parse(raw);
		const crossRefresh = String(data?.refreshToken || '').trim();
		const crossAccess = String(data?.accessToken || '').trim();
		if (!crossAccess || !crossRefresh) return null;
		// Only use if the refresh token actually changed (another tab refreshed)
		if (crossRefresh === usedRefreshToken) return null;
		return {
			accessToken: crossAccess,
			refreshToken: crossRefresh,
			adminAccessToken: String(data?.adminAccessToken || '').trim() || undefined,
			adminRefreshToken: String(data?.adminRefreshToken || '').trim() || undefined,
			adminAccessTokenExpiresAt: String(data?.adminAccessTokenExpiresAt || '').trim() || undefined,
			adminRefreshTokenExpiresAt: String(data?.adminRefreshTokenExpiresAt || '').trim() || undefined,
		};
	} catch {
		return null;
	}
}

/**
 * Wait for another tab to complete its refresh and broadcast the result.
 * Checks localStorage immediately, then retries every 300ms up to maxWaitMs.
 * This closes the race window where our 401 arrives before the winning tab
 * has finished its successful refresh and written to CROSS_TAB_REFRESH_KEY.
 */
async function waitForCrossTabRecovery(usedRefreshToken: string, maxWaitMs: number): Promise<RefreshResult> {
	// Check immediately first
	const immediate = readCrossTabTokens(usedRefreshToken);
	if (immediate) return immediate;

	// Retry with short intervals
	const interval = 300;
	const maxRetries = Math.ceil(maxWaitMs / interval);
	for (let i = 0; i < maxRetries; i++) {
		await new Promise((resolve) => setTimeout(resolve, interval));
		const result = readCrossTabTokens(usedRefreshToken);
		if (result) return result;
	}
	return null;
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
				// Cross-tab race recovery: our request failed (the refresh token was already
				// consumed by another tab). The winning tab writes new tokens to localStorage
				// (CROSS_TAB_REFRESH_KEY) — but it might still be in-flight when we get our
				// 401 back (401 is faster than a successful refresh). So we check immediately,
				// then wait up to 2s with retries to give the winning tab time to finish.
				const recovered = await waitForCrossTabRecovery(actualRefreshToken, 2000);
				if (recovered) {
					console.debug(LOG_PREFIX, 'refresh: failed locally but another tab refreshed — recovered from localStorage');
					_refreshFailedAt = 0;
					const existing = userStore.getState().userToken;
					userStore.getState().actions.setUserToken({
						accessToken: recovered.accessToken,
						refreshToken: recovered.refreshToken,
						adminAccessToken: recovered.adminAccessToken || existing?.adminAccessToken,
						adminRefreshToken: recovered.adminRefreshToken || existing?.adminRefreshToken,
						adminAccessTokenExpiresAt: recovered.adminAccessTokenExpiresAt || existing?.adminAccessTokenExpiresAt,
						adminRefreshTokenExpiresAt: recovered.adminRefreshTokenExpiresAt || existing?.adminRefreshTokenExpiresAt,
					});
					return recovered;
				}
				console.warn(LOG_PREFIX, 'refresh: failed (no cross-tab recovery after wait)', { status: resp.status });
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
			// Same cross-tab race recovery for network errors
			const recovered = await waitForCrossTabRecovery(refreshToken, 2000);
			if (recovered) {
				console.debug(LOG_PREFIX, 'refresh: network error but recovered from cross-tab localStorage');
				_refreshFailedAt = 0;
				const existing = userStore.getState().userToken;
				userStore.getState().actions.setUserToken({
					accessToken: recovered.accessToken,
					refreshToken: recovered.refreshToken,
					adminAccessToken: recovered.adminAccessToken || existing?.adminAccessToken,
					adminRefreshToken: recovered.adminRefreshToken || existing?.adminRefreshToken,
					adminAccessTokenExpiresAt: recovered.adminAccessTokenExpiresAt || existing?.adminAccessTokenExpiresAt,
					adminRefreshTokenExpiresAt: recovered.adminRefreshTokenExpiresAt || existing?.adminRefreshTokenExpiresAt,
				});
				return recovered;
			}
			console.warn(LOG_PREFIX, 'refresh: error', err instanceof Error ? err.message : err);
			_refreshFailedAt = Date.now();
			return null;
		}
	})().finally(() => {
		_refreshPromise = null;
	});

	return _refreshPromise;
}

// ─── Login redirect (Zustand intent pattern) ────────────────────────────────
//
// Instead of calling window.location.replace() directly (which causes page reloads,
// redirect loops, and 414 errors), callers write a "redirect intent" to a Zustand store.
// A React component (<SessionRedirectGuard>) subscribes to this store and performs the
// actual navigation via React Router's navigate() — soft navigation, no reload.

type RedirectIntent = {
	/** The path the user should return to after login */
	returnPath: string;
	/** Monotonic counter to distinguish repeated intents for the same path */
	seq: number;
} | null;

type RedirectIntentStore = {
	intent: RedirectIntent;
	/** Write a redirect intent. Idempotent — duplicate paths within the same seq are ignored. */
	requestRedirect: (returnPath: string) => void;
	/** Clear the intent after the React component has consumed it. */
	clearIntent: () => void;
};

let _seq = 0;

export const useRedirectIntentStore = create<RedirectIntentStore>((set, get) => ({
	intent: null,
	requestRedirect: (returnPath: string) => {
		const current = get().intent;
		// Deduplicate: if the same returnPath is already pending, don't bump seq
		if (current && current.returnPath === returnPath) return;
		_seq += 1;
		console.warn(LOG_PREFIX, 'redirect: intent', { returnPath, seq: _seq });
		set({ intent: { returnPath, seq: _seq } });
	},
	clearIntent: () => set({ intent: null }),
}));

/**
 * 获取当前路由路径（hash 路由感知）。
 */
export function currentRoutePath(): string {
	return resolveCurrentRoutePath(GLOBAL_CONFIG.routerHistory);
}

/**
 * 请求重定向到登录页，自动携带当前路由作为 redirect 目标。
 *
 * 不再直接操作 window.location — 而是写入 Zustand store，
 * 由 <SessionRedirectGuard> 组件通过 React Router 软导航消费。
 */
export function redirectToLoginWithReturn(): void {
	const loginPath = '/auth/login';
	const current = currentRoutePath();
	const pathOnly = current.split('?')[0];
	// Already on login page — no need to redirect
	if (pathOnly === loginPath || pathOnly.endsWith(loginPath)) {
		console.debug(LOG_PREFIX, 'redirect: skipped (already on login page)');
		return;
	}
	useRedirectIntentStore.getState().requestRedirect(current);
}

/**
 * 登录成功后清除 redirect intent，防止登录后又被重定向。
 */
export function resetLoginRedirectFlag(): void {
	useRedirectIntentStore.getState().clearIntent();
}
