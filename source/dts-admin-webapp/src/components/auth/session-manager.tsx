import { useEffect, useMemo, useRef } from "react";
import { readStorageValue } from "@dts-session-core/storage";
import { toast } from "sonner";
import { fetchCurrentSession, redirectToLoginWithReturn } from "@/auth/session-auth";
import { ADMIN_LEGACY_SESSION_KEYS, ADMIN_SESSION_KEYS } from "@/auth/session-keys";
import { usePortalSession, useUserActions, useUserInfo } from "@/store/userStore";
import userService from "@/api/services/userService";

const LOG_PREFIX = "[session:admin]";
const SESSION_TIMEOUT_MINUTES = Math.max(
	1,
	Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_ADMIN_SESSION_TIMEOUT ?? "10"),
);
const SESSION_TIMEOUT_MS = SESSION_TIMEOUT_MINUTES * 60 * 1000;

function readLastActivity(): number {
	try {
		const stored = readStorageValue(
			ADMIN_SESSION_KEYS.lastActivity,
			ADMIN_LEGACY_SESSION_KEYS.lastActivity,
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
	if (ts - lastWriteRef.current < 10_000) return;
	lastWriteRef.current = ts;
	try {
		localStorage.setItem(ADMIN_SESSION_KEYS.lastActivity, String(ts));
	} catch {}
}

export default function SessionManager() {
	const session = usePortalSession();
	const user = useUserInfo();
	const { markSessionChecking, setAuthenticatedSession, setSession, clearUserInfoAndToken } = useUserActions();
	const lastActivityRef = useRef<number>(readLastActivity());
	const lastActivityWriteRef = useRef<number>(0);
	const lastReasonRef = useRef(session.reason);
	const logoutInProgressRef = useRef(false);
	const sessionRef = useRef(session);

	const isLoggedIn = useMemo(() => session.authenticated, [session.authenticated]);

	useEffect(() => {
		sessionRef.current = session;
	}, [session]);

	useEffect(() => {
		const syncCurrentSession = async (mode: "bootstrap" | "silent") => {
			if (mode === "bootstrap") {
				markSessionChecking();
			}
			try {
				const current = await fetchCurrentSession();
				if (current.authenticated) {
					logoutInProgressRef.current = false;
					setAuthenticatedSession(current);
					return;
				}
				clearUserInfoAndToken(mode === "bootstrap" ? "anonymous" : "expired");
				return;
			} catch (error) {
				console.warn(LOG_PREFIX, "probe: keep current session after transient failure", { mode, error });
				if (mode === "bootstrap") {
					if (sessionRef.current.authenticated) {
						setSession({
							...sessionRef.current,
							initialized: true,
							checking: false,
						});
						return;
					}
					clearUserInfoAndToken("anonymous");
				}
			}
		};

		void syncCurrentSession("bootstrap");

		const handleFocus = () => {
			const now = Date.now();
			lastActivityRef.current = now;
			writeLastActivity(now, lastActivityWriteRef);
			void syncCurrentSession("silent");
		};

		const onStorage = (event: StorageEvent) => {
			if (event.key === ADMIN_SESSION_KEYS.lastActivity) {
				if (event.newValue) {
					const next = Number(event.newValue);
					if (next > 0) {
						lastActivityRef.current = next;
					}
				}
			}
		};

		window.addEventListener("focus", handleFocus);
		window.addEventListener("storage", onStorage);
		return () => {
			window.removeEventListener("focus", handleFocus);
			window.removeEventListener("storage", onStorage);
		};
	}, [clearUserInfoAndToken, markSessionChecking, setAuthenticatedSession, setSession]);

	useEffect(() => {
		if (!isLoggedIn) return;
		const updateActivity = () => {
			const now = Date.now();
			lastActivityRef.current = now;
			writeLastActivity(now, lastActivityWriteRef);
		};
		const events: Array<keyof WindowEventMap> = ["click", "keydown", "mousemove", "scroll", "touchstart"];
		events.forEach((event) => window.addEventListener(event, updateActivity, true));
		const visibilityHandler = () => {
			if (document.visibilityState === "visible") {
				updateActivity();
				void fetchCurrentSession()
					.then((current) => {
						if (current.authenticated) {
							setAuthenticatedSession(current);
						}
					})
					.catch((error) => {
						console.warn(LOG_PREFIX, "probe: visibility sync failed", error);
					});
			}
		};
		document.addEventListener("visibilitychange", visibilityHandler);
		return () => {
			events.forEach((event) => window.removeEventListener(event, updateActivity, true));
			document.removeEventListener("visibilitychange", visibilityHandler);
		};
	}, [isLoggedIn, setAuthenticatedSession]);

	useEffect(() => {
		if (!isLoggedIn) return;
		let timer: number | undefined;

		const logoutDueToIdle = () => {
			if (logoutInProgressRef.current) return;
			const storedActivity = readLastActivity();
			if (storedActivity > lastActivityRef.current) {
				lastActivityRef.current = storedActivity;
			}
			const idleFor = Date.now() - lastActivityRef.current;
			if (idleFor < SESSION_TIMEOUT_MS) {
				resetTimer();
				return;
			}
			logoutInProgressRef.current = true;
			console.warn(LOG_PREFIX, "logout: IDLE_TIMEOUT", { idleFor });
			const username = user?.username || user?.email || undefined;
			userService.logout(undefined, username, "IDLE_TIMEOUT").catch(() => undefined);
			clearUserInfoAndToken("expired");
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
	}, [clearUserInfoAndToken, isLoggedIn, user?.email, user?.username]);

	useEffect(() => {
		if (lastReasonRef.current === session.reason) return;
		lastReasonRef.current = session.reason;
		if (session.authenticated || !session.initialized) return;
		if (session.reason === "taken_over") {
			toast.error("账号已在其他位置登录，本会话已退出", { id: "session-conflict" });
			return;
		}
		if (session.reason === "expired") {
			toast.error("会话已过期，请重新登录", { id: "session-expired" });
		}
	}, [session.authenticated, session.initialized, session.reason]);

	return null;
}
