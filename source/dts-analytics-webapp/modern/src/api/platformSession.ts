export type PlatformTokens = {
	accessToken: string;
	refreshToken: string;
};

const DEFAULT_STORE_KEY = "userStore";

type AnyObject = Record<string, unknown>;

function readJson(raw: string | null): AnyObject | null {
	if (!raw) return null;
	try {
		const v = JSON.parse(raw);
		return v && typeof v === "object" ? (v as AnyObject) : null;
	} catch {
		return null;
	}
}

function asObject(v: unknown): AnyObject | null {
	return v && typeof v === "object" ? (v as AnyObject) : null;
}

function pickString(obj: AnyObject | null, keys: string[]): string {
	if (!obj) return "";
	for (const key of keys) {
		const v = obj[key];
		if (typeof v === "string" && v.trim()) return v.trim();
	}
	return "";
}

export function getPlatformTokens(storeKey: string = DEFAULT_STORE_KEY): PlatformTokens {
	const store = readJson(localStorage.getItem(storeKey));
	const state = asObject(store?.state);
	const userToken = asObject(state?.userToken);

	// Prefer portal access token for platform forward-auth (opaque token expected by dts-platform).
	// Fall back to adminAccessToken only when portal token is missing.
	const accessToken = pickString(userToken, ["accessToken", "access_token", "token", "adminAccessToken"]);
	// Refresh should use portal refresh token; adminRefreshToken is not accepted by platform refresh endpoint.
	const refreshToken = pickString(userToken, ["refreshToken", "refresh_token"]);
	return { accessToken, refreshToken };
}

export function setPlatformTokens(tokens: Partial<PlatformTokens>, storeKey: string = DEFAULT_STORE_KEY) {
	const store = readJson(localStorage.getItem(storeKey)) ?? { state: {}, version: 0 };
	const state = (asObject(store.state) ?? {}) as AnyObject;
	const userToken = (asObject(state.userToken) ?? {}) as AnyObject;

	if (tokens.accessToken !== undefined) userToken.accessToken = tokens.accessToken;
	if (tokens.refreshToken !== undefined) userToken.refreshToken = tokens.refreshToken;

	state.userToken = userToken;
	(store as AnyObject).state = state;
	localStorage.setItem(storeKey, JSON.stringify(store));
}

function pickTokenFromResponse(body: unknown): PlatformTokens | null {
	const obj = asObject(body);
	const data = asObject(obj?.data) ?? asObject(obj?.result) ?? asObject(obj?.payload);

	const accessToken =
		pickString(obj, ["accessToken", "access_token", "token"]) || pickString(data, ["accessToken", "access_token", "token"]);
	if (!accessToken) return null;

	const refreshToken =
		pickString(obj, ["refreshToken", "refresh_token"]) || pickString(data, ["refreshToken", "refresh_token"]);
	return { accessToken, refreshToken };
}

/**
 * BUG-005: Keep platform session alive while user is active in analytics.
 *
 * Two things must happen:
 * 1. Update localStorage lastActivity so platform SessionManager won't expire on return
 * 2. Periodically refresh the JWT so forward-auth doesn't reject API calls
 *
 * The platform SPA is fully unloaded when analytics opens (same-tab navigation),
 * so no platform code runs to handle token refresh — analytics must do it.
 */
const SESSION_ACTIVITY_KEY = "dts.session.lastActivity";
const DEFAULT_TOKEN_REFRESH_INTERVAL_MS = 4 * 60 * 1000;
const MIN_REFRESH_DELAY_MS = 30 * 1000;
const TOKEN_REFRESH_SKEW_MS = 60 * 1000;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let tokenRefreshTimer: ReturnType<typeof setTimeout> | null = null;

function decodeJwtExp(token?: string): number | null {
	if (!token) return null;
	try {
		const parts = token.split(".");
		if (parts.length < 2) return null;
		let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
		while (payload.length % 4 !== 0) payload += "=";
		const json = atob(payload);
		const obj = JSON.parse(json);
		return typeof obj?.exp === "number" ? obj.exp * 1000 : null;
	} catch {
		return null;
	}
}

function resolveConfiguredSessionTimeoutMinutes(): number {
	const value = Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_PORTAL_SESSION_TIMEOUT ?? "30");
	return Number.isFinite(value) && value > 0 ? value : 30;
}

export function computeNextRefreshDelayMs(accessToken?: string, sessionTimeoutMinutes: number = 30): number {
	const normalizedTimeout = Number.isFinite(sessionTimeoutMinutes) && sessionTimeoutMinutes > 0 ? sessionTimeoutMinutes : 30;
	const sessionKeepaliveDelayMs = (normalizedTimeout * 60 * 1000) / 2;
	const tokenExpiryMs = decodeJwtExp(accessToken);
	const tokenRefreshDelayMs =
		tokenExpiryMs == null ? Number.POSITIVE_INFINITY : Math.max(MIN_REFRESH_DELAY_MS, tokenExpiryMs - Date.now() - TOKEN_REFRESH_SKEW_MS);

	const nextDelayMs = Math.min(DEFAULT_TOKEN_REFRESH_INTERVAL_MS, sessionKeepaliveDelayMs, tokenRefreshDelayMs);
	return Math.max(MIN_REFRESH_DELAY_MS, Number.isFinite(nextDelayMs) ? nextDelayMs : DEFAULT_TOKEN_REFRESH_INTERVAL_MS);
}

function touchPlatformSession() {
	localStorage.setItem(SESSION_ACTIVITY_KEY, String(Date.now()));
}

function redirectToLogin() {
	const returnUrl = encodeURIComponent(window.location.pathname + window.location.search);
	window.location.href = `/#/auth/login?redirect=${returnUrl}`;
}

async function refreshTokenIfNeeded() {
	const { refreshToken } = getPlatformTokens();
	if (!refreshToken) {
		console.warn("[session] No refresh token available, redirecting to login");
		redirectToLogin();
		return;
	}
	try {
		const result = await refreshPlatformAccessToken(refreshToken);
		if (!result) {
			console.warn("[session] Token refresh returned null");
		}
	} catch (err) {
		console.warn("[session] Token refresh failed:", err);
	}
}

function scheduleTokenRefresh(delayMs: number) {
	if (tokenRefreshTimer) {
		clearTimeout(tokenRefreshTimer);
	}
	tokenRefreshTimer = setTimeout(async () => {
		await refreshTokenIfNeeded();
		const { accessToken } = getPlatformTokens();
		scheduleTokenRefresh(computeNextRefreshDelayMs(accessToken, resolveConfiguredSessionTimeoutMinutes()));
	}, delayMs);
}

export function startPlatformSessionHeartbeat() {
	if (heartbeatTimer) return;
	// Touch activity immediately
	touchPlatformSession();
	// Periodic activity heartbeat (every 60s)
	heartbeatTimer = setInterval(touchPlatformSession, 60_000);
	// Periodic portal session refresh — keeps both token and server-side session alive.
	const { accessToken } = getPlatformTokens();
	scheduleTokenRefresh(computeNextRefreshDelayMs(accessToken, resolveConfiguredSessionTimeoutMinutes()));
	// Touch on user interactions
	const events = ["click", "keydown", "scroll", "touchstart"] as const;
	const handler = () => touchPlatformSession();
	for (const event of events) {
		document.addEventListener(event, handler, { passive: true });
	}
}

export function stopPlatformSessionHeartbeat() {
	if (heartbeatTimer) {
		clearInterval(heartbeatTimer);
		heartbeatTimer = null;
	}
	if (tokenRefreshTimer) {
		clearTimeout(tokenRefreshTimer);
		tokenRefreshTimer = null;
	}
}

export async function refreshPlatformAccessToken(refreshToken: string): Promise<PlatformTokens | null> {
	const rt = String(refreshToken ?? "").trim();
	if (!rt) return null;

	const response = await fetch("/api/keycloak/auth/refresh", {
		method: "POST",
		credentials: "include",
		headers: { "content-type": "application/json", accept: "application/json" },
		body: JSON.stringify({ refreshToken: rt }),
	});
	if (!response.ok) {
		// If refresh fails, check if we have any valid token left
		const current = getPlatformTokens();
		if (!current.accessToken) {
			// No valid token remaining, redirect to platform login
			redirectToLogin();
		}
		return null;
	}

	const body = await response.json().catch(() => null);
	const tokens = pickTokenFromResponse(body);
	if (!tokens) return null;

	setPlatformTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken || rt });
	return { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken || rt };
}
