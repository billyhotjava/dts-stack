import { useCallback, useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { nextRefreshDelayMs } from "@dts-session-core/token";
import { readStorageValue } from "@dts-session-core/storage";
import { redirectToLoginWithReturn, refreshAccessToken } from "@/auth/session-auth";
import { ADMIN_LEGACY_SESSION_KEYS, ADMIN_SESSION_KEYS } from "@/auth/session-keys";
import { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";
import userService from "@/api/services/userService";

const genId = () => Math.random().toString(36).slice(2) + Date.now().toString(36);
const SESSION_TIMEOUT_MINUTES = Math.max(
	1,
	Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_ADMIN_SESSION_TIMEOUT ?? "10"),
);
const SESSION_TIMEOUT_MS = SESSION_TIMEOUT_MINUTES * 60 * 1000;
const SESSION_IDLE_GRACE_MS = 30 * 1000;

function readLastActivity(): number {
	try {
		const stored = readStorageValue(ADMIN_SESSION_KEYS.lastActivity, ADMIN_LEGACY_SESSION_KEYS.lastActivity, localStorage);
		if (stored) {
			const ts = Number(stored);
			if (ts > 0) return ts;
		}
	} catch {}
	return Date.now();
}

function writeLastActivity(ts: number, lastWriteRef: { current: number }) {
	if (ts - lastWriteRef.current < 10_000) return;
	lastWriteRef.current = ts;
	try {
		localStorage.setItem(ADMIN_SESSION_KEYS.lastActivity, String(ts));
	} catch {}
}

export function SessionManager() {
	const user = useUserInfo();
	const token = useUserToken();
	const { clearUserInfoAndToken } = useUserActions();

	const tokenRef = useRef(token);
	const userRef = useRef(user);

	useEffect(() => {
		tokenRef.current = token;
	}, [token]);

	useEffect(() => {
		userRef.current = user;
	}, [user]);

	const tabIdRef = useRef<string>(genId());
	const mySessionIdRef = useRef<string | null>(null);
	const lastActivityRef = useRef<number>(readLastActivity());
	const lastActivityWriteRef = useRef<number>(0);
	const logoutInProgressRef = useRef(false);
	const activitySinceRefreshRef = useRef<boolean>(false);
	const idleTimerRef = useRef<number | undefined>(undefined);

	const triggerAutoLogout = useCallback(() => {
		if (logoutInProgressRef.current) return;
		logoutInProgressRef.current = true;
		if (idleTimerRef.current) {
			window.clearTimeout(idleTimerRef.current);
			idleTimerRef.current = undefined;
		}
		activitySinceRefreshRef.current = false;
		const currentToken = tokenRef.current;
		const currentUser = userRef.current;
		const refreshToken = currentToken?.refreshToken;
		const username = currentUser?.username || currentUser?.email || undefined;
		if (refreshToken) {
			userService.logout(refreshToken, username, "IDLE_TIMEOUT").catch(() => undefined);
		}
		toast.error("长时间未操作，已自动退出，请重新登录", { id: "session-expired" });
		clearUserInfoAndToken();
		localStorage.setItem(ADMIN_SESSION_KEYS.logoutTs, String(Date.now()));
		redirectToLoginWithReturn();
	}, [clearUserInfoAndToken]);

	const isLoggedIn = useMemo(() => Boolean(token?.accessToken), [token?.accessToken]);
	const loginName = user?.username || user?.email || "";

	// Establish or update session marker on login
	useEffect(() => {
		if (!isLoggedIn) {
			mySessionIdRef.current = null;
			activitySinceRefreshRef.current = false;
			logoutInProgressRef.current = false;
			return;
		}
		const now = Date.now();
		lastActivityRef.current = now;
		writeLastActivity(now, lastActivityWriteRef);
		activitySinceRefreshRef.current = false;
		const current = localStorage.getItem(ADMIN_SESSION_KEYS.sessionId);
		if (!current) {
			const newId = `${loginName || "user"}#${genId()}#${tabIdRef.current}`;
			mySessionIdRef.current = newId;
			localStorage.setItem(ADMIN_SESSION_KEYS.sessionId, newId);
			if (loginName) localStorage.setItem(ADMIN_SESSION_KEYS.sessionUser, loginName);
		} else {
			// Adopt existing session id in case we opened new tab after login
			mySessionIdRef.current = current;
		}
	}, [isLoggedIn, loginName]);

	// Cross-tab session change detection (force single active session in browser)
	useEffect(() => {
		const onStorage = (e: StorageEvent) => {
			if (!e.key) return;
			if (e.key === ADMIN_SESSION_KEYS.sessionId) {
				const newId = e.newValue;
				if (isLoggedIn && newId && newId !== mySessionIdRef.current && !logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("账号已在其他位置登录，本会话已退出", { id: "session-conflict" });
					clearUserInfoAndToken();
					// Mark a logout broadcast for other listeners
					localStorage.setItem(ADMIN_SESSION_KEYS.logoutTs, String(Date.now()));
					redirectToLoginWithReturn();
				}
			}
			if (e.key === ADMIN_SESSION_KEYS.logoutTs && e.newValue) {
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
	}, [isLoggedIn, clearUserInfoAndToken]);

	useEffect(() => {
		if (!isLoggedIn) return;
		const updateActivity = () => {
			const now = Date.now();
			lastActivityRef.current = now;
			writeLastActivity(now, lastActivityWriteRef);
			activitySinceRefreshRef.current = true;
		};
		const events: Array<keyof DocumentEventMap> = ["click", "keydown", "mousemove", "scroll", "touchstart"];
		events.forEach((event) => window.addEventListener(event, updateActivity, { passive: true, capture: true }));
		const visibilityHandler = () => {
			if (document.visibilityState === "visible") {
				const now = Date.now();
				lastActivityRef.current = now;
				writeLastActivity(now, lastActivityWriteRef);
				activitySinceRefreshRef.current = true;
			}
		};
		document.addEventListener("visibilitychange", visibilityHandler);
		return () => {
			events.forEach((event) => window.removeEventListener(event, updateActivity, true));
			document.removeEventListener("visibilitychange", visibilityHandler);
		};
	}, [isLoggedIn]);

	// Heartbeat/refresh to detect timeout and keep token fresh
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
			if (logoutInProgressRef.current) {
				cancelled = true;
				return;
			}
			const storedActivity = readLastActivity();
			if (storedActivity > lastActivityRef.current) {
				lastActivityRef.current = storedActivity;
			}
			const idleFor = Date.now() - lastActivityRef.current;
			const forcedExpiry = idleFor >= SESSION_TIMEOUT_MS + SESSION_IDLE_GRACE_MS;
			const hadActivity = activitySinceRefreshRef.current;

			if (forcedExpiry) {
				triggerAutoLogout();
				cancelled = true;
				return;
			}

			if (!hadActivity && !forcedExpiry) {
				const wait = Math.max(SESSION_IDLE_GRACE_MS, SESSION_TIMEOUT_MS - idleFor);
				schedule(wait);
				return;
			}

			const refreshed = await refreshAccessToken();
			if (refreshed?.accessToken) {
				activitySinceRefreshRef.current = false;
				if (!cancelled) {
					const delay = nextRefreshDelayMs(refreshed.accessToken);
					schedule(delay);
				}
			} else {
				// Refresh failed — could be cross-tab race (another tab consumed the token).
				// Do NOT call triggerAutoLogout (which writes LOGOUT_TS and forces all tabs
				// to log out). Only redirect THIS tab; let other tabs handle their own cycle.
				if (!logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("会话已过期，请重新登录", { id: "session-expired" });
					clearUserInfoAndToken();
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
	}, [isLoggedIn, token?.refreshToken, token?.accessToken, triggerAutoLogout]);

	useEffect(() => {
		if (!isLoggedIn) {
			if (idleTimerRef.current) window.clearTimeout(idleTimerRef.current);
			idleTimerRef.current = undefined;
			return;
		}

		const resetTimer = () => {
			if (logoutInProgressRef.current) return;
			const now = Date.now();
			lastActivityRef.current = now;
			writeLastActivity(now, lastActivityWriteRef);
			if (idleTimerRef.current) window.clearTimeout(idleTimerRef.current);
			idleTimerRef.current = window.setTimeout(() => {
				triggerAutoLogout();
				idleTimerRef.current = undefined;
			}, SESSION_TIMEOUT_MS);
		};

		resetTimer();
		const events: Array<keyof WindowEventMap> = ["click", "keydown", "mousemove", "scroll", "touchstart"];
		events.forEach((event) => window.addEventListener(event, resetTimer, true));

		return () => {
			if (idleTimerRef.current) window.clearTimeout(idleTimerRef.current);
			idleTimerRef.current = undefined;
			events.forEach((event) => window.removeEventListener(event, resetTimer, true));
		};
	}, [isLoggedIn, triggerAutoLogout]);

	return null;
}

export default SessionManager;
