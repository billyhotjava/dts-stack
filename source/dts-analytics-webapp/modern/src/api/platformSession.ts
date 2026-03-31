import { parsePersistedUserStoreSnapshot, readPersistedTokens } from "@dts-session-core/persisted-store";
import { buildLoginRedirectHref, currentRoutePath } from "@dts-session-core/route";
import { readStorageValue } from "@dts-session-core/storage";
import { decodeJwtExp } from "@dts-session-core/token";
import { PLATFORM_LEGACY_USER_STORE_KEYS, PLATFORM_SESSION_KEYS } from "./platformSessionKeys";

export type PlatformTokens = {
	accessToken: string;
	refreshToken: string;
};

function pickTokenFromResponse(body: unknown): PlatformTokens | null {
	if (!body || typeof body !== "object") return null;
	const obj = body as Record<string, unknown>;
	const data =
		(obj.data && typeof obj.data === "object" ? (obj.data as Record<string, unknown>) : null) ||
		(obj.result && typeof obj.result === "object" ? (obj.result as Record<string, unknown>) : null) ||
		(obj.payload && typeof obj.payload === "object" ? (obj.payload as Record<string, unknown>) : null);

	const pick = (source: Record<string, unknown> | null, keys: string[]) => {
		if (!source) return "";
		for (const key of keys) {
			const value = source[key];
			if (typeof value === "string" && value.trim()) return value.trim();
		}
		return "";
	};

	const accessToken = pick(obj, ["accessToken", "access_token", "token"]) || pick(data, ["accessToken", "access_token", "token"]);
	if (!accessToken) return null;
	const refreshToken = pick(obj, ["refreshToken", "refresh_token"]) || pick(data, ["refreshToken", "refresh_token"]);
	return { accessToken, refreshToken };
}

export function getPlatformTokens(): PlatformTokens {
	const { accessToken, refreshToken } = readPersistedTokens(
		PLATFORM_SESSION_KEYS.userStore,
		PLATFORM_LEGACY_USER_STORE_KEYS,
		localStorage,
	);
	return { accessToken, refreshToken };
}

export function setPlatformTokens(tokens: Partial<PlatformTokens>) {
	const raw = readStorageValue(PLATFORM_SESSION_KEYS.userStore, PLATFORM_LEGACY_USER_STORE_KEYS, localStorage);
	const parsed = parsePersistedUserStoreSnapshot(raw) ?? { userInfo: {}, userToken: {} };
	const next = {
		state: {
			userInfo: parsed.userInfo,
			userToken: {
				...parsed.userToken,
				...(tokens.accessToken !== undefined ? { accessToken: tokens.accessToken } : {}),
				...(tokens.refreshToken !== undefined ? { refreshToken: tokens.refreshToken } : {}),
			},
		},
		version: 0,
	};
	localStorage.setItem(PLATFORM_SESSION_KEYS.userStore, JSON.stringify(next));
}

const OPAQUE_TOKEN_REFRESH_INTERVAL_MS = 60 * 1000;
const DEFAULT_TOKEN_REFRESH_INTERVAL_MS = 4 * 60 * 1000;
const MIN_REFRESH_DELAY_MS = 30 * 1000;
const TOKEN_REFRESH_SKEW_MS = 60 * 1000;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let tokenRefreshTimer: ReturnType<typeof setTimeout> | null = null;
let refreshPromise: Promise<PlatformTokens | null> | null = null;

function resolveConfiguredSessionTimeoutMinutes(): number {
	const value = Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_PORTAL_SESSION_TIMEOUT ?? "30");
	return Number.isFinite(value) && value > 0 ? value : 30;
}

export function computeNextRefreshDelayMs(accessToken?: string, sessionTimeoutMinutes: number = 30): number {
	const normalizedTimeout = Number.isFinite(sessionTimeoutMinutes) && sessionTimeoutMinutes > 0 ? sessionTimeoutMinutes : 30;
	const sessionKeepaliveDelayMs = (normalizedTimeout * 60 * 1000) / 2;
	const tokenExpiryMs = decodeJwtExp(accessToken);
	if (tokenExpiryMs == null) {
		return Math.max(MIN_REFRESH_DELAY_MS, Math.min(OPAQUE_TOKEN_REFRESH_INTERVAL_MS, sessionKeepaliveDelayMs));
	}
	const tokenRefreshDelayMs = Math.max(MIN_REFRESH_DELAY_MS, tokenExpiryMs - Date.now() - TOKEN_REFRESH_SKEW_MS);
	const nextDelayMs = Math.min(DEFAULT_TOKEN_REFRESH_INTERVAL_MS, sessionKeepaliveDelayMs, tokenRefreshDelayMs);
	return Math.max(MIN_REFRESH_DELAY_MS, Number.isFinite(nextDelayMs) ? nextDelayMs : DEFAULT_TOKEN_REFRESH_INTERVAL_MS);
}

function touchPlatformSession() {
	localStorage.setItem(PLATFORM_SESSION_KEYS.lastActivity, String(Date.now()));
}

function redirectToLogin() {
	window.location.href = buildLoginRedirectHref("/#/auth/login", currentRoutePath("browser"));
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
	touchPlatformSession();
	heartbeatTimer = setInterval(touchPlatformSession, 60_000);
	const { accessToken } = getPlatformTokens();
	scheduleTokenRefresh(computeNextRefreshDelayMs(accessToken, resolveConfiguredSessionTimeoutMinutes()));
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
	if (refreshPromise) return refreshPromise;

	refreshPromise = (async () => {
		const response = await fetch("/api/keycloak/auth/refresh", {
			method: "POST",
			credentials: "include",
			headers: { "content-type": "application/json", accept: "application/json" },
			body: JSON.stringify({ refreshToken: rt }),
		});
		if (!response.ok) {
			const current = getPlatformTokens();
			if (!current.accessToken) {
				redirectToLogin();
			}
			return null;
		}

		const body = await response.json().catch(() => null);
		const tokens = pickTokenFromResponse(body);
		if (!tokens) return null;

		setPlatformTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken || rt });
		return { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken || rt };
	})().finally(() => {
		queueMicrotask(() => {
			refreshPromise = null;
		});
	});

	return refreshPromise;
}
