import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { resolveLoginHref } from "@/routes/constants";
import { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";
import userService from "@/api/services/userService";

const STORAGE_KEYS = {
	SESSION_ID: "dts.session.id",
	SESSION_USER: "dts.session.user",
	LOGOUT_TS: "dts.session.logoutTs",
	LAST_ACTIVITY: "dts.session.lastActivity",
	TOKEN_SYNC: "dts.session.tokenSync",
} as const;

const SESSION_TIMEOUT_MINUTES = Math.max(
	1,
	Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_PORTAL_SESSION_TIMEOUT ?? "30"),
);
const SESSION_TIMEOUT_MS = SESSION_TIMEOUT_MINUTES * 60 * 1000;
const SESSION_IDLE_GRACE_MS = 30 * 1000;

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
			if (e.key === STORAGE_KEYS.LOGOUT_TS && e.newValue) {
				if (isLoggedIn && !logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("账号已在其他位置退出", { id: "session-conflict" });
					clearUserInfoAndToken();
					window.location.replace(resolveLoginHref());
				}
			}
		};
		window.addEventListener("storage", onStorage);
		return () => window.removeEventListener("storage", onStorage);
	}, [isLoggedIn, clearUserInfoAndToken, setUserToken, token]);

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
			const idleFor = Date.now() - lastActivityRef.current;
			if (idleFor > SESSION_TIMEOUT_MS + SESSION_IDLE_GRACE_MS) {
				if (!logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					const refreshToken = token?.refreshToken;
					const username = user?.username || user?.email || undefined;
					if (refreshToken) {
						userService.logout(refreshToken, username, "IDLE_TIMEOUT").catch(() => undefined);
					}
					toast.error("会话已过期，请重新登录", { id: "session-expired" });
					clearUserInfoAndToken();
					localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
					window.location.replace(resolveLoginHref());
				}
				cancelled = true;
				return;
			}
			try {
				const res = await userService.refresh(token.refreshToken!);
				const nextAccess = (res as any)?.accessToken;
				const nextRefresh = (res as any)?.refreshToken;
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
						adminAccessToken: nextAdminAccess,
						adminRefreshToken: nextAdminRefresh,
						adminAccessTokenExpiresAt: adminAccessExpiresAt,
						adminRefreshTokenExpiresAt: adminRefreshExpiresAt,
					};
					setUserToken(newToken);
					broadcastTokenSync(newToken);
				}
				if (!cancelled) {
					schedule(nextRefreshDelayMs(nextAccess || token.accessToken));
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
					window.location.replace(resolveLoginHref());
				}
				cancelled = true;
			}
		};

		schedule(nextRefreshDelayMs(token.accessToken));

		return () => {
			cancelled = true;
			if (timer) window.clearTimeout(timer);
		};
	}, [isLoggedIn, token?.refreshToken, token?.accessToken, setUserToken, clearUserInfoAndToken, user?.username, user?.email]);

	// Pure idle timer — logout after SESSION_TIMEOUT_MS of no interaction
	useEffect(() => {
		if (!isLoggedIn) return;
		let timer: number | undefined;

		const logoutDueToIdle = () => {
			if (logoutInProgressRef.current) return;
			logoutInProgressRef.current = true;
			const refreshToken = token?.refreshToken;
			const username = user?.username || user?.email || undefined;
			if (refreshToken) {
				userService.logout(refreshToken, username, "IDLE_TIMEOUT").catch(() => undefined);
			}
			toast.error("长时间未操作，已自动退出，请重新登录", { id: "session-expired" });
			clearUserInfoAndToken();
			localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
			window.location.replace(resolveLoginHref());
		};

		const resetTimer = () => {
			if (logoutInProgressRef.current) return;
			if (timer) window.clearTimeout(timer);
			timer = window.setTimeout(logoutDueToIdle, SESSION_TIMEOUT_MS);
		};

		const events: Array<keyof WindowEventMap> = ["click", "keydown", "mousemove", "scroll", "touchstart"];
		events.forEach((event) => window.addEventListener(event, resetTimer, true));
		resetTimer();

		return () => {
			if (timer) window.clearTimeout(timer);
			events.forEach((event) => window.removeEventListener(event, resetTimer, true));
		};
	}, [isLoggedIn, clearUserInfoAndToken, token?.refreshToken, user?.username, user?.email]);

	return null;
}
