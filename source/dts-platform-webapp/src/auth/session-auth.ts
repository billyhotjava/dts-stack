/**
 * session-auth.ts — 统一会话鉴权模块
 *
 * 整个平台 **唯一** 的 token 刷新入口和登录重定向入口。
 * apiClient / SessionManager / analyticsApi / LoginAuthGuard 全部委托到这里。
 *
 * 核心保证：
 *  1. 同一时刻最多只有一个 refresh 请求在飞（single-flight lock）
 *  2. 同一时刻最多只触发一次登录重定向（redirect guard）
 *  3. 重定向始终携带当前路由作为 ?redirect= 参数（hash 路由感知）
 */

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

// ─── Single-flight token refresh ─────────────────────────────────────────────

let _refreshPromise: Promise<RefreshResult> | null = null;

/**
 * 刷新平台 access token。
 *
 * - 如果已有一个 refresh 请求在飞，所有后续调用者复用同一个 Promise
 * - 成功后自动写入 userStore（所有消费者通过 Zustand 订阅拿到新 token）
 * - 使用原生 fetch 而非 axios，避免进入 apiClient 响应拦截器的递归循环
 */
export async function refreshAccessToken(): Promise<RefreshResult> {
	const { userToken } = userStore.getState();
	const refreshToken = String(userToken?.refreshToken || '').trim();
	if (!refreshToken) return null;

	// Single-flight: 复用已有的刷新请求
	if (_refreshPromise) return _refreshPromise;

	_refreshPromise = (async (): Promise<RefreshResult> => {
		try {
			const apiBase = GLOBAL_CONFIG.apiBaseUrl || '/api';
			const resp = await fetch(`${apiBase}/keycloak/auth/refresh`, {
				method: 'POST',
				credentials: 'include',
				headers: { 'content-type': 'application/json', accept: 'application/json' },
				body: JSON.stringify({ refreshToken }),
			});
			if (!resp.ok) return null;

			const body = await resp.json().catch(() => null);
			const data = body?.data ?? body?.result ?? body?.payload ?? body;

			const nextAccess = String(data?.accessToken || data?.access_token || data?.token || '').trim();
			if (!nextAccess) return null;

			const nextRefresh = String(data?.refreshToken || data?.refresh_token || '').trim();
			const adminAccessToken = String(data?.adminAccessToken || '').trim() || undefined;
			const adminRefreshToken = String(data?.adminRefreshToken || '').trim() || undefined;
			const adminAccessTokenExpiresAt = String(data?.adminAccessTokenExpiresAt || '').trim() || undefined;
			const adminRefreshTokenExpiresAt = String(data?.adminRefreshTokenExpiresAt || '').trim() || undefined;

			const existing = userStore.getState().userToken;
			userStore.getState().actions.setUserToken({
				accessToken: nextAccess,
				refreshToken: nextRefresh || refreshToken,
				adminAccessToken: adminAccessToken || existing?.adminAccessToken,
				adminRefreshToken: adminRefreshToken || existing?.adminRefreshToken,
				adminAccessTokenExpiresAt: adminAccessTokenExpiresAt || existing?.adminAccessTokenExpiresAt,
				adminRefreshTokenExpiresAt: adminRefreshTokenExpiresAt || existing?.adminRefreshTokenExpiresAt,
			});

			return {
				accessToken: nextAccess,
				refreshToken: nextRefresh || refreshToken,
				adminAccessToken,
				adminRefreshToken,
				adminAccessTokenExpiresAt,
				adminRefreshTokenExpiresAt,
			};
		} catch {
			return null;
		}
	})().finally(() => {
		// 延迟清除，让同一 tick 的并发 await 拿到相同结果
		setTimeout(() => {
			_refreshPromise = null;
		}, 50);
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
	if (window.location.hash) {
		return window.location.hash.replace(/^#/, '') || '/';
	}
	return window.location.pathname + window.location.search;
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
	const returnUrl = encodeURIComponent(currentRoutePath());
	window.location.replace(`${resolveLoginHref()}?redirect=${returnUrl}`);
}

/**
 * 登录成功后重置重定向守卫，使后续会话过期可以再次触发重定向。
 */
export function resetLoginRedirectFlag(): void {
	_redirecting = false;
}
