import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { LOGIN_ROUTE } from "@/routes/constants";
import { useRouter } from "@/routes/hooks";
import { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";
import userService from "@/api/services/userService";

const STORAGE_KEYS = {
	SESSION_ID: "dts.session.id",
	SESSION_USER: "dts.session.user",
	LOGOUT_TS: "dts.session.logoutTs",
} as const;

const SESSION_TIMEOUT_MINUTES = Math.max(
	1,
	Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_PORTAL_SESSION_TIMEOUT ?? "10"),
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
	const MIN_DELAY = 30 * 1000; // 30s
	const DEFAULT_DELAY = 4 * 60 * 1000; // 4m fallback
	const SKEW = 60 * 1000; // refresh 60s before expiry
	const expMs = decodeJwtExp(accessToken);
	if (!expMs) return DEFAULT_DELAY;
	const now = Date.now();
	const ms = Math.max(MIN_DELAY, expMs - now - SKEW);
	return ms;
}

export default function SessionManager() {
	const router = useRouter();
	const user = useUserInfo();
	const token = useUserToken();
	const { setUserToken, clearUserInfoAndToken } = useUserActions();

	const tabIdRef = useRef<string>(genId());
	const mySessionIdRef = useRef<string | null>(null);
	const lastActivityRef = useRef<number>(Date.now());
	const logoutInProgressRef = useRef(false);

	const isLoggedIn = useMemo(() => Boolean(token?.accessToken), [token?.accessToken]);
	const loginName = user?.username || user?.email || "";

	useEffect(() => {
		if (!isLoggedIn) {
			mySessionIdRef.current = null;
			logoutInProgressRef.current = false;
			return;
		}
		lastActivityRef.current = Date.now();
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

	useEffect(() => {
		const onStorage = (e: StorageEvent) => {
			if (!e.key) return;
			if (e.key === STORAGE_KEYS.SESSION_ID) {
				const newId = e.newValue;
				if (isLoggedIn && newId && newId !== mySessionIdRef.current && !logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("账号已在其他位置登录，本会话已退出", { id: "session-conflict" });
					clearUserInfoAndToken();
					localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
					router.replace(LOGIN_ROUTE);
				}
			}
			if (e.key === STORAGE_KEYS.LOGOUT_TS && e.newValue) {
				if (isLoggedIn && !logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("账号已在其他位置登录，本会话已退出", { id: "session-conflict" });
					clearUserInfoAndToken();
					router.replace(LOGIN_ROUTE);
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
					toast.error("会话已过期，请重新登录", { id: "session-expired" });
					clearUserInfoAndToken();
					localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
					router.replace(LOGIN_ROUTE);
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
					setUserToken({
						accessToken: nextAccess,
						refreshToken: nextRefresh || token.refreshToken,
						adminAccessToken: nextAdminAccess,
						adminRefreshToken: nextAdminRefresh,
						adminAccessTokenExpiresAt: adminAccessExpiresAt,
						adminRefreshTokenExpiresAt: adminRefreshExpiresAt,
					});
				}
				if (!cancelled) {
					const delay = nextRefreshDelayMs(nextAccess || token.accessToken);
					schedule(delay);
				}
			} catch (err) {
				if (!logoutInProgressRef.current) {
					logoutInProgressRef.current = true;
					toast.error("会话已过期，请重新登录", { id: "session-expired" });
					clearUserInfoAndToken();
					localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
					router.replace(LOGIN_ROUTE);
				}
				cancelled = true;
			}
		};

		schedule(nextRefreshDelayMs(token.accessToken));

		return () => {
			cancelled = true;
			if (timer) window.clearTimeout(timer);
		};
	}, [isLoggedIn, token?.refreshToken, token?.accessToken, setUserToken, clearUserInfoAndToken, router]);

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
			router.replace(LOGIN_ROUTE);
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
	}, [isLoggedIn, clearUserInfoAndToken, router, token?.refreshToken, user?.username, user?.email]);

	return null;
}
