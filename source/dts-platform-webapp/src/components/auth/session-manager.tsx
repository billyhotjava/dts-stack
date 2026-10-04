import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { isWithinLoginProbeGrace, refreshPortalSessionIfPossible } from "@/api/apiClient";
import { getPortalSessionStatus, type PortalSessionStatus } from "@/api/platformApi";
import { resolveCurrentAppPath, resolveLoginHref } from "@/routes/constants";
import useUserStore, { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";
import { isDevFallbackAccessToken } from "@/utils/devAuthTokens";
import {
	markPortalSessionLogout,
	PORTAL_SESSION_STORAGE_KEYS,
	readPortalSessionTimestamp,
	wasPortalLogoutBroadcastRecently,
} from "@/utils/portalSessionStorage";
import {
	buildSessionLeaderLease,
	isSessionLeaderActive,
	parseSessionLeaderLease,
	shouldAcquireSessionLeadership,
} from "./sessionLeadership.helpers";

const STORAGE_KEYS = PORTAL_SESSION_STORAGE_KEYS;

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
const SESSION_STATUS_POLL_MS = Math.max(10 * 1000, Number(import.meta.env.VITE_SESSION_STATUS_POLL_MS ?? 15 * 1000));

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

function resolveSessionEndReason(status?: PortalSessionStatus): "CONCURRENT" | "EXPIRED" | "LOGOUT" {
	if (status?.reason === "CONCURRENT") return "CONCURRENT";
	if (status?.reason === "LOGOUT") return "LOGOUT";
	return "EXPIRED";
}

function isDefinitiveInactiveStatus(status?: PortalSessionStatus | null): boolean {
	if (!status || status.authenticated !== false) {
		return false;
	}
	return (
		status.reason === "CONCURRENT" ||
		status.reason === "EXPIRED" ||
		status.reason === "LOGOUT" ||
		status.remainingSeconds === 0
	);
}

function nextRefreshRetryDelayMs(attempt: number): number {
	return Math.min(30_000, Math.max(10_000, attempt * 10_000));
}

function nextRefreshDelayMs(accessToken?: string, tokenExpiresAt?: number): number {
	const MIN_DELAY = 60 * 1000; // 1 min
	// For opaque portal tokens, refresh at half the portal session timeout minus 1 min skew.
	const DEFAULT_DELAY = Math.max(MIN_DELAY, Math.floor(SESSION_TIMEOUT_MS / 2) - 60_000);
	const SKEW = 60 * 1000; // refresh 60s before expiry
	const expMs = typeof tokenExpiresAt === "number" && tokenExpiresAt > 0 ? tokenExpiresAt : decodeJwtExp(accessToken);
	if (!expMs) return DEFAULT_DELAY;
	const now = Date.now();
	const ms = Math.max(MIN_DELAY, expMs - now - SKEW);
	return ms;
}

function readLastActivity(): number {
	return readPortalSessionTimestamp(STORAGE_KEYS.LAST_ACTIVITY) || Date.now();
}

function resolveSharedLastActivity(fallback: number): number {
	return readPortalSessionTimestamp(STORAGE_KEYS.LAST_ACTIVITY) || fallback;
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
		localStorage.setItem(STORAGE_KEYS.TOKEN_SYNC, JSON.stringify({ ...tokens, ts: Date.now() }));
	} catch {}
}

function wasLogoutTriggeredRecently(): boolean {
	const accessToken = useUserStore.getState().userToken?.accessToken;
	return wasPortalLogoutBroadcastRecently(5000, Date.now(), accessToken);
}

function normalizeAccessToken(accessToken?: string | null): string {
	const raw = String(accessToken || "").trim();
	return raw.startsWith("Bearer ") ? raw.slice(7).trim() : raw;
}

function isStillCurrentAccessToken(expectedAccessToken?: string | null): boolean {
	const expected = normalizeAccessToken(expectedAccessToken);
	if (!expected) {
		return true;
	}
	const current = normalizeAccessToken(useUserStore.getState().userToken?.accessToken);
	return current === expected;
}

function finishSession(
	clearUserInfoAndToken: () => void,
	logoutInProgressRef: { current: boolean },
	reason: "CONCURRENT" | "EXPIRED" | "LOGOUT" | "UNKNOWN" = "EXPIRED",
	broadcast = true,
	expectedAccessToken?: string | null,
) {
	if (logoutInProgressRef.current) {
		return;
	}
	if (!isStillCurrentAccessToken(expectedAccessToken)) {
		return;
	}
	logoutInProgressRef.current = true;
	const isConcurrent = reason === "CONCURRENT";
	const accessToken = useUserStore.getState().userToken?.accessToken;
	toast.error(isConcurrent ? "账号已在其他位置登录，当前会话已失效" : "会话已过期，请重新登录", {
		id: isConcurrent ? "session-conflict" : "session-expired",
	});
	clearUserInfoAndToken();
	if (broadcast) {
		markPortalSessionLogout(Date.now(), accessToken);
	}
	window.location.replace(resolveLoginHref(resolveCurrentAppPath()));
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

	const isLoggedIn = useMemo(
		() => Boolean(token?.authenticated || token?.accessToken),
		[token?.authenticated, token?.accessToken],
	);
	const isDevFallbackSession = useMemo(() => isDevFallbackAccessToken(token?.accessToken), [token?.accessToken]);
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
					if (synced?.authenticated) {
						setUserToken({
							...token,
							authenticated: true,
							tokenExpiresAt: synced.tokenExpiresAt || token?.tokenExpiresAt,
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
				const currentAccessToken = useUserStore.getState().userToken?.accessToken;
				if (
					isLoggedIn &&
					!logoutInProgressRef.current &&
					wasPortalLogoutBroadcastRecently(5000, Date.now(), currentAccessToken)
				) {
					logoutInProgressRef.current = true;
					toast.error("当前会话已失效，请重新登录", { id: "session-expired" });
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

	// Probe the current portal session while the page is open so takeover is visible
	// even if the user is idle and no business API is being called.
	useEffect(() => {
		if (!isLoggedIn || isDevFallbackSession) return;
		let cancelled = false;
		let timer: number | undefined;
		let probing = false;
		// 对明确的 EXPIRED / CONCURRENT / LOGOUT 立即失效；
		// 只有无原因的 authenticated=false 才走 2 次确认阈值，避免短暂探活抖动误杀。
		let consecutiveExpiredFails = 0;

		const schedule = (delay = SESSION_STATUS_POLL_MS) => {
			if (cancelled) return;
			timer = window.setTimeout(() => {
				void probe();
			}, delay);
		};

		const probe = async () => {
			if (cancelled || probing || logoutInProgressRef.current) {
				return;
			}
			// 登录后 grace window：portal_session 刚保存对本次探活事务可能不可见，
			// 直接跳过并稍后重试，避免误判为 EXPIRED 把用户本机踢掉。
			if (isWithinLoginProbeGrace()) {
				schedule(1_000);
				return;
			}
			probing = true;
			try {
				const status = await getPortalSessionStatus();
				if (cancelled || logoutInProgressRef.current) {
					return;
				}
				if (!status?.authenticated) {
					if (wasLogoutTriggeredRecently()) {
						cancelled = true;
						return;
					}
					const reason = resolveSessionEndReason(status);
					if (isDefinitiveInactiveStatus(status)) {
						finishSession(clearUserInfoAndToken, logoutInProgressRef, reason);
						cancelled = true;
						return;
					}
					consecutiveExpiredFails += 1;
					if (consecutiveExpiredFails >= 2) {
						finishSession(clearUserInfoAndToken, logoutInProgressRef, reason);
						cancelled = true;
						return;
					}
					console.warn(
						`[session] probe authenticated=false (attempt ${consecutiveExpiredFails}/2) — waiting for next tick to confirm`,
					);
				} else {
					consecutiveExpiredFails = 0;
				}
			} catch (error) {
				console.warn("[session] status probe failed", error);
			} finally {
				probing = false;
			}
			schedule();
		};

		const handleForegroundProbe = () => {
			if (document.visibilityState === "hidden") {
				return;
			}
			if (timer) {
				window.clearTimeout(timer);
			}
			void probe();
		};

		schedule();
		window.addEventListener("focus", handleForegroundProbe);
		document.addEventListener("visibilitychange", handleForegroundProbe);

		return () => {
			cancelled = true;
			if (timer) {
				window.clearTimeout(timer);
			}
			window.removeEventListener("focus", handleForegroundProbe);
			document.removeEventListener("visibilitychange", handleForegroundProbe);
		};
	}, [isLoggedIn, isDevFallbackSession, clearUserInfoAndToken]);

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
		if (!isLoggedIn || isDevFallbackSession) return;
		let cancelled = false;
		let timer: number | undefined;
		let consecutiveRefreshFailures = 0;

		const schedule = (delay: number) => {
			if (cancelled) return;
			timer = window.setTimeout(run, delay);
		};

		const run = async () => {
			if (cancelled) return;
			const currentToken = useUserStore.getState().userToken || token;
			if (!currentToken?.authenticated) {
				cancelled = true;
				return;
			}
			const currentLease = readLeaderLease();
			if (
				!currentLease ||
				currentLease.tabId !== tabIdRef.current ||
				!isSessionLeaderActive(currentLease, Date.now())
			) {
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
				const refreshed = await refreshPortalSessionIfPossible();
				if (!refreshed?.authenticated) {
					throw new Error("portal_refresh_failed");
				}
				consecutiveRefreshFailures = 0;
				broadcastTokenSync(refreshed);
				if (!cancelled) {
					const refreshDelay = nextRefreshDelayMs(undefined, refreshed.tokenExpiresAt ?? currentToken.tokenExpiresAt);
					schedule(refreshDelay);
				}
			} catch (error) {
				// Don't immediately logout — another tab may have already refreshed.
				// Check if we received a synced token recently.
				try {
					const synced = localStorage.getItem(STORAGE_KEYS.TOKEN_SYNC);
					if (synced) {
						const parsed = JSON.parse(synced);
						if (parsed?.ts && Date.now() - parsed.ts < 30_000 && parsed.authenticated) {
							// Another tab refreshed recently — adopt the fresh expiry and retry later
							consecutiveRefreshFailures = 0;
							setUserToken({
								...currentToken,
								authenticated: true,
								tokenExpiresAt: parsed.tokenExpiresAt || currentToken.tokenExpiresAt,
							});
							if (!cancelled) {
								schedule(nextRefreshDelayMs(undefined, parsed.tokenExpiresAt));
							}
							return;
						}
					}
				} catch {}
				if (wasLogoutTriggeredRecently()) {
					cancelled = true;
					return;
				}
				let status: PortalSessionStatus | null = null;
				try {
					status = await getPortalSessionStatus();
				} catch (statusError) {
					console.warn("[session] refresh follow-up status probe failed", statusError);
				}
				if (cancelled || logoutInProgressRef.current) {
					return;
				}
				if (isDefinitiveInactiveStatus(status)) {
					finishSession(clearUserInfoAndToken, logoutInProgressRef, resolveSessionEndReason(status ?? undefined));
					cancelled = true;
					return;
				}
				consecutiveRefreshFailures += 1;
				const retryDelay = nextRefreshRetryDelayMs(consecutiveRefreshFailures);
				console.warn(`[session] refresh failed (attempt ${consecutiveRefreshFailures}), retrying`, error);
				schedule(retryDelay);
			}
		};

		const initialDelay = nextRefreshDelayMs(undefined, token.tokenExpiresAt);
		schedule(initialDelay);

		return () => {
			cancelled = true;
			if (timer) window.clearTimeout(timer);
		};
	}, [isLoggedIn, isDevFallbackSession, token, setUserToken, clearUserInfoAndToken]);

	return null;
}
