import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { resolveCurrentAppPath, resolveLoginHref } from "@/routes/constants";
import { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";
import userService from "@/api/services/userService";
import {
	buildSessionLeaderLease,
	isSessionLeaderActive,
	parseSessionLeaderLease,
	shouldAcquireSessionLeadership,
} from "./sessionLeadership.helpers";

const STORAGE_KEYS = {
	SESSION_ID: "dts.platform.session.id",
	SESSION_USER: "dts.platform.session.user",
	LOGOUT_TS: "dts.platform.session.logoutTs",
	LAST_ACTIVITY: "dts.platform.session.lastActivity",
	TOKEN_SYNC: "dts.platform.session.tokenSync",
	REFRESH_LEADER: "dts.platform.session.refreshLeader",
} as const;

const SESSION_TIMEOUT_MINUTES = Math.max(
	1,
	Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_PORTAL_SESSION_TIMEOUT ?? "30"),
);
const SESSION_TIMEOUT_MS = SESSION_TIMEOUT_MINUTES * 60 * 1000;
const SESSION_IDLE_GRACE_MS = 30 * 1000;
const LEADER_LEASE_MS = 45 * 1000;
const LEADER_HEARTBEAT_MS = 15 * 1000;
const FOLLOWER_RECHECK_MS = 15 * 1000;
const LEADER_CONFIRM_MS = 250;

const genId = () => Math.random().toString(36).slice(2) + Date.now().toString(36);

function decodeJwtExp(token?: string): number | null {
	if (!token) return null;
	try {
		const parts = token.split(".");
		if (parts.length < 2) return null;
		let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
		while (payload.length % 4 !== 0) payload += "=";
		const json = atob(payload);
		const obj = JSON.parse(json);
		if (obj && typeof obj.exp === "number") {
			return obj.exp * 1000; // to ms
		}
		return null;
	} catch {
		return null;
	}
}

function nextRefreshDelayMs(accessToken?: string): number {
	const MIN_DELAY = 60 * 1000; // 1 min
	// For non-JWT tokens, refresh at half the session timeout minus 1 min skew
	const DEFAULT_DELAY = Math.max(MIN_DELAY, Math.floor(SESSION_TIMEOUT_MS / 2) - 60_000);
	const SKEW = 60 * 1000; // refresh 60s before expiry
	const expMs = decodeJwtExp(accessToken);
	if (!expMs) return DEFAULT_DELAY;
	const now = Date.now();
	const ms = Math.max(MIN_DELAY, expMs - now - SKEW);
	return ms;
}

function readLastActivity(): number {
	try {
		const stored = localStorage.getItem(STORAGE_KEYS.LAST_ACTIVITY);
		if (stored) {
			const ts = Number(stored);
			if (ts > 0) return ts;
		}
	} catch {}
	return Date.now();
}

function resolveSharedLastActivity(fallback: number): number {
	try {
		const stored = localStorage.getItem(STORAGE_KEYS.LAST_ACTIVITY);
		if (stored) {
			const ts = Number(stored);
			if (ts > 0) return ts;
		}
	} catch {}
	return fallback;
}

function readLeaderLease() {
	try {
		return parseSessionLeaderLease(localStorage.getItem(STORAGE_KEYS.REFRESH_LEADER));
	} catch {
		return null;
	}
}

function writeLastActivity(ts: number, lastWriteRef: { current: number }) {
	// Throttle writes to localStorage: at most once per 10 seconds
	if (ts - lastWriteRef.current < 10_000) return;
	lastWriteRef.current = ts;
	try {
		localStorage.setItem(STORAGE_KEYS.LAST_ACTIVITY, String(ts));
	} catch {}
}

/** Broadcast refreshed tokens to other tabs via localStorage */
function broadcastTokenSync(tokens: Record<string, unknown>) {
	try {
		localStorage.setItem(
			STORAGE_KEYS.TOKEN_SYNC,
			JSON.stringify({ ...tokens, ts: Date.now() }),
		);
	} catch {}
}

export default function SessionManager() {
	const user = useUserInfo();
	const token = useUserToken();
	const { setUserToken, clearUserInfoAndToken } = useUserActions();

	const tabIdRef = useRef<string>(genId());
	const mySessionIdRef = useRef<string | null>(null);
	const lastActivityRef = useRef<number>(readLastActivity());
	const lastActivityWriteRef = useRef<number>(0);
	const logoutInProgressRef = useRef(false);
	const isLeaderRef = useRef(false);

	const isLoggedIn = useMemo(() => Boolean(token?.accessToken), [token?.accessToken]);
	const loginName = user?.username || user?.email || "";

	useEffect(() => {
		if (!isLoggedIn) {
			mySessionIdRef.current = null;
			logoutInProgressRef.current = false;
			return;
		}
		const now = Date.now();
		lastActivityRef.current = now;
		writeLastActivity(now, lastActivityWriteRef);
		const current = localStorage.getItem(STORAGE_KEYS.SESSION_ID);
		if (!current) {
			const newId = `${loginName || "user"}#${genId()}#${tabIdRef.current}`;
			mySessionIdRef.current = newId;
			localStorage.setItem(STORAGE_KEYS.SESSION_ID, newId);
			if (loginName) localStorage.setItem(STORAGE_KEYS.SESSION_USER, loginName);
		} else {
			mySessionIdRef.current = current;
		}
	}, [isLoggedIn, loginName]);

	// Listen for cross-tab events: token sync and logout broadcast
	useEffect(() => {
		const onStorage = (e: StorageEvent) => {
			if (!e.key) return;
			// Another tab refreshed tokens — adopt them to avoid stale-token 401
			if (e.key === STORAGE_KEYS.TOKEN_SYNC && e.newValue && isLoggedIn) {
				try {
					const synced = JSON.parse(e.newValue);
					if (synced?.accessToken && synced?.refreshToken) {
						setUserToken({
							...token,
							accessToken: synced.accessToken,
							refreshToken: synced.refreshToken,
							adminAccessToken: synced.adminAccessToken || token?.adminAccessToken,
							adminRefreshToken: synced.adminRefreshToken || token?.adminRefreshToken,
							adminAccessTokenExpiresAt: synced.adminAccessTokenExpiresAt || token?.adminAccessTokenExpiresAt,
							adminRefreshTokenExpiresAt: synced.adminRefreshTokenExpiresAt || token?.adminRefreshTokenExpiresAt,
						});
					}
				} catch {}
				return;
			}
			if (e.key === STORAGE_KEYS.LAST_ACTIVITY && e.newValue) {
				const ts = Number(e.newValue);
				if (ts > 0) {
					lastActivityRef.current = ts;
				}
				return;
			}
			if (e.key === STORAGE_KEYS.REFRESH_LEADER) {
				const lease = parseSessionLeaderLease(e.newValue);
				isLeaderRef.current = lease?.tabId === tabIdRef.current && isSessionLeaderActive(lease, Date.now());
				return;
			}
			if (e.key === STORAGE_KEYS.LOGOUT_TS && e.newValue) {
				if (isLoggedIn && !logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("账号已在其他位置退出", { id: "session-conflict" });
					clearUserInfoAndToken();
					window.location.replace(resolveLoginHref(resolveCurrentAppPath()));
				}
			}
		};
		window.addEventListener("storage", onStorage);
		return () => window.removeEventListener("storage", onStorage);
	}, [isLoggedIn, clearUserInfoAndToken, setUserToken, token]);

	useEffect(() => {
		if (!isLoggedIn) {
			isLeaderRef.current = false;
			try {
				const lease = readLeaderLease();
				if (lease?.tabId === tabIdRef.current) {
					localStorage.removeItem(STORAGE_KEYS.REFRESH_LEADER);
				}
			} catch {}
			return;
		}

		const claimLeadership = () => {
			const now = Date.now();
			const currentLease = readLeaderLease();
			if (!shouldAcquireSessionLeadership(currentLease, tabIdRef.current, now)) {
				isLeaderRef.current = currentLease?.tabId === tabIdRef.current && isSessionLeaderActive(currentLease, now);
				return;
			}
			const nextLease = buildSessionLeaderLease(tabIdRef.current, now, LEADER_LEASE_MS, LEADER_CONFIRM_MS);
			try {
				localStorage.setItem(STORAGE_KEYS.REFRESH_LEADER, JSON.stringify(nextLease));
				const confirmedLease = readLeaderLease();
				isLeaderRef.current = confirmedLease?.tabId === tabIdRef.current && isSessionLeaderActive(confirmedLease, now);
			} catch {
				isLeaderRef.current = false;
			}
		};

		claimLeadership();
		const timer = window.setInterval(() => {
			claimLeadership();
		}, LEADER_HEARTBEAT_MS);

		return () => {
			window.clearInterval(timer);
			try {
				const lease = readLeaderLease();
				if (lease?.tabId === tabIdRef.current) {
					localStorage.removeItem(STORAGE_KEYS.REFRESH_LEADER);
				}
			} catch {}
			isLeaderRef.current = false;
		};
	}, [isLoggedIn]);

	// Track user activity
	useEffect(() => {
		if (!isLoggedIn) return;
		const updateActivity = () => {
			const now = Date.now();
			lastActivityRef.current = now;
			writeLastActivity(now, lastActivityWriteRef);
		};
		const events: Array<keyof DocumentEventMap> = ["click", "keydown", "mousemove", "scroll", "touchstart"];
		events.forEach((event) => window.addEventListener(event, updateActivity, { passive: true, capture: true }));
		const visibilityHandler = () => {
			if (document.visibilityState === "visible") {
				const now = Date.now();
				lastActivityRef.current = now;
				writeLastActivity(now, lastActivityWriteRef);
			}
		};
		document.addEventListener("visibilitychange", visibilityHandler);
		return () => {
			events.forEach((event) => window.removeEventListener(event, updateActivity, true));
			document.removeEventListener("visibilitychange", visibilityHandler);
		};
	}, [isLoggedIn]);

	// Token refresh loop
	useEffect(() => {
		if (!isLoggedIn || !token?.refreshToken) return;
		let cancelled = false;
		let timer: number | undefined;

		const schedule = (delay: number) => {
			if (cancelled) return;
			timer = window.setTimeout(run, delay);
		};

		const run = async () => {
			if (cancelled) return;
			const currentLease = readLeaderLease();
			if (!currentLease || currentLease.tabId !== tabIdRef.current || !isSessionLeaderActive(currentLease, Date.now())) {
				isLeaderRef.current = false;
				schedule(FOLLOWER_RECHECK_MS);
				return;
			}
			isLeaderRef.current = true;
			const sharedLastActivity = resolveSharedLastActivity(lastActivityRef.current);
			lastActivityRef.current = sharedLastActivity;
			const idleFor = Date.now() - sharedLastActivity;
			if (idleFor > SESSION_TIMEOUT_MS + SESSION_IDLE_GRACE_MS) {
				// Browser-side inactivity only stops proactive refresh. The backend remains
				// the single source of truth for session expiry and will return 401 once the
				// shared portal session actually times out.
				schedule(SESSION_IDLE_GRACE_MS);
				return;
			}
			try {
				const res = await userService.refresh(token.refreshToken!);
				const nextAccess = (res as any)?.accessToken;
				const nextRefresh = (res as any)?.refreshToken;
				// Derive tokenExpiresAt from backend expiresIn (Keycloak token lifetime)
				const expiresInSec = Number((res as any)?.expiresIn ?? 0);
				const nextTokenExpiresAt = expiresInSec > 0 ? Date.now() + expiresInSec * 1000 : token.tokenExpiresAt;
				const normalizeDate = (value: unknown): string | undefined =>
					typeof value === "string" && value.trim() ? value.trim() : undefined;
				const nextAdminAccess = (res as any)?.adminAccessToken || token.adminAccessToken;
				const nextAdminRefresh = (res as any)?.adminRefreshToken || token.adminRefreshToken;
				const adminAccessExpiresAt =
					normalizeDate((res as any)?.adminAccessTokenExpiresAt) ?? token.adminAccessTokenExpiresAt;
				const adminRefreshExpiresAt =
					normalizeDate((res as any)?.adminRefreshTokenExpiresAt) ?? token.adminRefreshTokenExpiresAt;
				if (nextAccess) {
					const newToken = {
						accessToken: nextAccess,
						refreshToken: nextRefresh || token.refreshToken,
						tokenExpiresAt: nextTokenExpiresAt,
						adminAccessToken: nextAdminAccess,
						adminRefreshToken: nextAdminRefresh,
						adminAccessTokenExpiresAt: adminAccessExpiresAt,
						adminRefreshTokenExpiresAt: adminRefreshExpiresAt,
					};
					setUserToken(newToken);
					broadcastTokenSync(newToken);
				}
				if (!cancelled) {
					// Use expiresIn from Keycloak for precise scheduling
					const refreshDelay = expiresInSec > 0
						? Math.max(60_000, expiresInSec * 1000 - 60_000)
						: nextRefreshDelayMs(nextAccess || token.accessToken);
					schedule(refreshDelay);
				}
			} catch (err) {
				// Don't immediately logout — another tab may have already refreshed.
				// Check if we received a synced token recently.
				try {
					const synced = localStorage.getItem(STORAGE_KEYS.TOKEN_SYNC);
					if (synced) {
						const parsed = JSON.parse(synced);
						if (parsed?.ts && Date.now() - parsed.ts < 30_000 && parsed.accessToken) {
							// Another tab refreshed recently — adopt its token and retry later
							setUserToken({
								...token,
								accessToken: parsed.accessToken,
								refreshToken: parsed.refreshToken || token.refreshToken,
								adminAccessToken: parsed.adminAccessToken || token.adminAccessToken,
								adminRefreshToken: parsed.adminRefreshToken || token.adminRefreshToken,
							});
							if (!cancelled) {
								schedule(nextRefreshDelayMs(parsed.accessToken));
							}
							return;
						}
					}
				} catch {}
				if (!logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("会话已过期，请重新登录", { id: "session-expired" });
					clearUserInfoAndToken();
					localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
					window.location.replace(resolveLoginHref(resolveCurrentAppPath()));
				}
				cancelled = true;
			}
		};

		// Use tokenExpiresAt (from Keycloak expiresIn) for precise initial scheduling.
		// For opaque tokens without expiresIn, fall back to heuristic.
		const initialDelay = (() => {
			if (token.tokenExpiresAt && token.tokenExpiresAt > 0) {
				const msUntilExpiry = token.tokenExpiresAt - Date.now();
				// Refresh 60s before expiry, minimum 30s
				return Math.max(30_000, msUntilExpiry - 60_000);
			}
			return nextRefreshDelayMs(token.accessToken);
		})();
		schedule(initialDelay);

		return () => {
			cancelled = true;
			if (timer) window.clearTimeout(timer);
		};
	}, [isLoggedIn, token?.refreshToken, token?.accessToken, token?.tokenExpiresAt, setUserToken, clearUserInfoAndToken, user?.username, user?.email]);

	return null;
}
