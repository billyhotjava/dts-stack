import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { useRouter } from "@/routes/hooks";
import { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";
import userService from "@/api/services/userService";

const STORAGE_KEYS = {
	SESSION_ID: "dts.session.id",
	SESSION_USER: "dts.session.user",
	LOGOUT_TS: "dts.session.logoutTs",
} as const;

const genId = () => Math.random().toString(36).slice(2) + Date.now().toString(36);
const SESSION_TIMEOUT_MINUTES = Math.max(
	1,
	Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_ADMIN_SESSION_TIMEOUT ?? "10"),
);
const SESSION_TIMEOUT_MS = SESSION_TIMEOUT_MINUTES * 60 * 1000;
const SESSION_IDLE_GRACE_MS = 30 * 1000;

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
	const MIN_DELAY = 30 * 1000; // 30s
	const DEFAULT_DELAY = 4 * 60 * 1000; // 4m fallback
	const SKEW = 60 * 1000; // refresh 60s before expiry
	const expMs = decodeJwtExp(accessToken);
	if (!expMs) return DEFAULT_DELAY;
	const now = Date.now();
	const ms = Math.max(MIN_DELAY, expMs - now - SKEW);
	return ms;
}

export function SessionManager() {
	const router = useRouter();
	const user = useUserInfo();
	const token = useUserToken();
	const { setUserToken, clearUserInfoAndToken } = useUserActions();

	const tabIdRef = useRef<string>(genId());
	const mySessionIdRef = useRef<string | null>(null);
	const lastActivityRef = useRef<number>(Date.now());
	const logoutInProgressRef = useRef(false);
	const activitySinceRefreshRef = useRef(true);
	const idleTimerRef = useRef<number>();

	const isLoggedIn = useMemo(() => Boolean(token?.accessToken), [token?.accessToken]);
	const loginName = user?.username || user?.email || "";

	// Establish or update session marker on login
	useEffect(() => {
		if (!isLoggedIn) {
			mySessionIdRef.current = null;
			activitySinceRefreshRef.current = true;
			logoutInProgressRef.current = false;
			return;
		}
		lastActivityRef.current = Date.now();
		activitySinceRefreshRef.current = true;
		const current = localStorage.getItem(STORAGE_KEYS.SESSION_ID);
		if (!current) {
			const newId = `${loginName || "user"}#${genId()}#${tabIdRef.current}`;
			mySessionIdRef.current = newId;
			localStorage.setItem(STORAGE_KEYS.SESSION_ID, newId);
			if (loginName) localStorage.setItem(STORAGE_KEYS.SESSION_USER, loginName);
		} else {
			// Adopt existing session id in case we opened new tab after login
			mySessionIdRef.current = current;
		}
	}, [isLoggedIn, loginName]);

	// Cross-tab session change detection (force single active session in browser)
	useEffect(() => {
		const onStorage = (e: StorageEvent) => {
			if (!e.key) return;
			if (e.key === STORAGE_KEYS.SESSION_ID) {
				const newId = e.newValue;
				if (isLoggedIn && newId && newId !== mySessionIdRef.current && !logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("账号已在其他位置登录，本会话已退出", { id: "session-conflict" });
					clearUserInfoAndToken();
					// Mark a logout broadcast for other listeners
					localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
					router.replace("/auth/login");
				}
			}
			if (e.key === STORAGE_KEYS.LOGOUT_TS && e.newValue) {
				if (isLoggedIn && !logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("账号已在其他位置登录，本会话已退出", { id: "session-conflict" });
					clearUserInfoAndToken();
					router.replace("/auth/login");
				}
			}
		};
		window.addEventListener("storage", onStorage);
		return () => window.removeEventListener("storage", onStorage);
	}, [isLoggedIn, clearUserInfoAndToken, router]);

	useEffect(() => {
		if (!isLoggedIn) return;
		const updateActivity = () => {
			lastActivityRef.current = Date.now();
			activitySinceRefreshRef.current = true;
		};
		const events: Array<keyof DocumentEventMap> = ["click", "keydown", "mousemove", "scroll", "touchstart"];
		events.forEach((event) => window.addEventListener(event, updateActivity, { passive: true, capture: true }));
		const visibilityHandler = () => {
			if (document.visibilityState === "visible") {
				lastActivityRef.current = Date.now();
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
			const idleFor = Date.now() - lastActivityRef.current;
			const forcedExpiry = idleFor >= SESSION_TIMEOUT_MS + SESSION_IDLE_GRACE_MS;
			const hadActivity = activitySinceRefreshRef.current;

			if (!hadActivity && !forcedExpiry) {
				const wait = Math.max(SESSION_IDLE_GRACE_MS, SESSION_TIMEOUT_MS - idleFor);
				schedule(wait);
				return;
			}

			try {
				const res = await userService.refresh(token.refreshToken!);
				const nextAccess = (res as any)?.accessToken;
				const nextRefresh = (res as any)?.refreshToken;
				const delay = nextRefreshDelayMs(nextAccess || token.accessToken);
				if (nextAccess) {
					setUserToken({ accessToken: nextAccess, refreshToken: nextRefresh || token.refreshToken });
				}
				activitySinceRefreshRef.current = false;
				if (!cancelled) {
					schedule(delay);
				}
			} catch (err) {
				toast.error("会话已过期，请重新登录", { id: "session-expired" });
				clearUserInfoAndToken();
				localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
				lastActivityRef.current = Date.now();
				cancelled = true;
				router.replace("/auth/login");
			}
		};

		schedule(nextRefreshDelayMs(token.accessToken));

		return () => {
			cancelled = true;
			if (timer) window.clearTimeout(timer);
		};
	}, [isLoggedIn, token?.refreshToken, token?.accessToken, setUserToken, clearUserInfoAndToken, router]);

	useEffect(() => {
		if (!isLoggedIn) {
			if (idleTimerRef.current) window.clearTimeout(idleTimerRef.current);
			idleTimerRef.current = undefined;
			return;
		}

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
			router.replace("/auth/login");
		};

		const resetTimer = () => {
			if (logoutInProgressRef.current) return;
			if (idleTimerRef.current) window.clearTimeout(idleTimerRef.current);
			idleTimerRef.current = window.setTimeout(logoutDueToIdle, SESSION_TIMEOUT_MS);
		};

		resetTimer();
		const events: Array<keyof WindowEventMap> = ["click", "keydown", "mousemove", "scroll", "touchstart"];
		events.forEach((event) => window.addEventListener(event, resetTimer, true));

		return () => {
			if (idleTimerRef.current) window.clearTimeout(idleTimerRef.current);
			idleTimerRef.current = undefined;
			events.forEach((event) => window.removeEventListener(event, resetTimer, true));
		};
	}, [isLoggedIn, clearUserInfoAndToken, router, token?.refreshToken, user?.username, user?.email]);

	return null;
}

export default SessionManager;
