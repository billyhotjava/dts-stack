import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { nextRefreshDelayMs } from "@dts-session-core/token";
import { readStorageValue } from "@dts-session-core/storage";
import { refreshAccessToken, redirectToLoginWithReturn } from "@/auth/session-auth";
import { PLATFORM_LEGACY_SESSION_KEYS, PLATFORM_SESSION_KEYS } from "@/auth/session-keys";
import { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";
import userService from "@/api/services/userService";
import { hasPersistedSessionChanged, parsePersistedUserStoreSnapshot } from "./sessionSync.helpers";

const SESSION_TIMEOUT_MINUTES = Math.max(
	1,
	Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_PORTAL_SESSION_TIMEOUT ?? "30"),
);
const SESSION_TIMEOUT_MS = SESSION_TIMEOUT_MINUTES * 60 * 1000;
const SESSION_IDLE_GRACE_MS = 30 * 1000;

const genId = () => Math.random().toString(36).slice(2) + Date.now().toString(36);

function readLastActivity(): number {
	try {
		const stored = readStorageValue(
			PLATFORM_SESSION_KEYS.lastActivity,
			PLATFORM_LEGACY_SESSION_KEYS.lastActivity,
			localStorage,
		);
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
		localStorage.setItem(PLATFORM_SESSION_KEYS.lastActivity, String(ts));
	} catch {}
}

export default function SessionManager() {
	const user = useUserInfo();
	const token = useUserToken();
	const { setUserInfo, setUserToken, clearUserInfoAndToken } = useUserActions();

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
		const current = localStorage.getItem(PLATFORM_SESSION_KEYS.sessionId);
		if (!current) {
			const newId = `${loginName || "user"}#${genId()}#${tabIdRef.current}`;
			mySessionIdRef.current = newId;
			localStorage.setItem(PLATFORM_SESSION_KEYS.sessionId, newId);
			if (loginName) localStorage.setItem(PLATFORM_SESSION_KEYS.sessionUser, loginName);
		} else {
			mySessionIdRef.current = current;
		}
	}, [isLoggedIn, loginName]);

	useEffect(() => {
		const onStorage = (e: StorageEvent) => {
			if (!e.key) return;
			if (e.key === PLATFORM_SESSION_KEYS.userStore && e.newValue) {
				const nextSnapshot = parsePersistedUserStoreSnapshot(e.newValue);
				const currentSnapshot = { userInfo: user, userToken: token };
				if (hasPersistedSessionChanged(currentSnapshot, nextSnapshot)) {
					if (nextSnapshot?.userInfo && Object.keys(nextSnapshot.userInfo).length > 0) {
						setUserInfo(nextSnapshot.userInfo as any);
					}
					if (nextSnapshot?.userToken && Object.keys(nextSnapshot.userToken).length > 0) {
						setUserToken(nextSnapshot.userToken);
					}
				}
				return;
			}
			if (e.key === PLATFORM_SESSION_KEYS.sessionId) {
				const newId = e.newValue;
				if (isLoggedIn && newId && newId !== mySessionIdRef.current && !logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("账号已在其他位置登录，本会话已退出", { id: "session-conflict" });
					clearUserInfoAndToken();
					localStorage.setItem(PLATFORM_SESSION_KEYS.logoutTs, String(Date.now()));
					redirectToLoginWithReturn();
				}
			}
			if (e.key === PLATFORM_SESSION_KEYS.logoutTs && e.newValue) {
				if (isLoggedIn && !logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("账号已在其他位置登录，本会话已退出", { id: "session-conflict" });
					clearUserInfoAndToken();
					redirectToLoginWithReturn();
				}
			}
		};
		window.addEventListener("storage", onStorage);
		return () => window.removeEventListener("storage", onStorage);
	}, [isLoggedIn, clearUserInfoAndToken, setUserInfo, setUserToken, token, user]);

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
			// Re-read from localStorage in case analytics heartbeat updated it
			const storedActivity = readLastActivity();
			if (storedActivity > lastActivityRef.current) {
				lastActivityRef.current = storedActivity;
			}
			const idleFor = Date.now() - lastActivityRef.current;
			if (idleFor > SESSION_TIMEOUT_MS + SESSION_IDLE_GRACE_MS) {
				if (!logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("会话已过期，请重新登录", { id: "session-expired" });
					clearUserInfoAndToken();
					localStorage.setItem(PLATFORM_SESSION_KEYS.logoutTs, String(Date.now()));
					redirectToLoginWithReturn();
				}
				cancelled = true;
				return;
			}
			const forcedExpiry = idleFor >= SESSION_TIMEOUT_MS + SESSION_IDLE_GRACE_MS;
			const nearingTimeout = idleFor >= SESSION_TIMEOUT_MS - SESSION_IDLE_GRACE_MS;
			const shouldBackoff =
				!forcedExpiry &&
				(document.visibilityState === "hidden" && idleFor > SESSION_TIMEOUT_MS / 2 ? true : nearingTimeout);
			if (shouldBackoff) {
				schedule(SESSION_IDLE_GRACE_MS);
				return;
			}
			// Use the shared single-flight refresh — same lock as apiClient and analyticsApi.
			// This prevents concurrent refresh-token consumption when multiple callers race.
			const result = await refreshAccessToken();
			if (result) {
				// refreshAccessToken already wrote to userStore; just reschedule.
				if (!cancelled) {
					const delay = nextRefreshDelayMs(result.accessToken);
					schedule(delay);
				}
			} else {
				// Refresh failed (token expired/consumed/server error)
				if (!logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("会话已过期，请重新登录", { id: "session-expired" });
					clearUserInfoAndToken();
					localStorage.setItem(PLATFORM_SESSION_KEYS.logoutTs, String(Date.now()));
					redirectToLoginWithReturn();
				}
				cancelled = true;
			}
		};

		schedule(nextRefreshDelayMs(token.accessToken));

		return () => {
			cancelled = true;
			if (timer) window.clearTimeout(timer);
		};
	}, [isLoggedIn, token?.refreshToken, token?.accessToken, setUserToken, clearUserInfoAndToken]);

	useEffect(() => {
		if (!isLoggedIn) return;
		let timer: number | undefined;

		const logoutDueToIdle = () => {
			if (logoutInProgressRef.current) return;
			// Re-read from localStorage in case analytics heartbeat updated it
			const storedActivity = readLastActivity();
			if (storedActivity > lastActivityRef.current) {
				lastActivityRef.current = storedActivity;
			}
			const idleFor = Date.now() - lastActivityRef.current;
			if (idleFor < SESSION_TIMEOUT_MS) {
				// Not actually idle — analytics was active. Reset timer.
				resetTimer();
				return;
			}
			logoutInProgressRef.current = true;
			const refreshToken = token?.refreshToken;
			const username = user?.username || user?.email || undefined;
			if (refreshToken) {
				userService.logout(refreshToken, username, "IDLE_TIMEOUT").catch(() => undefined);
			}
			toast.error("长时间未操作，已自动退出，请重新登录", { id: "session-expired" });
			clearUserInfoAndToken();
			localStorage.setItem(PLATFORM_SESSION_KEYS.logoutTs, String(Date.now()));
			redirectToLoginWithReturn();
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
