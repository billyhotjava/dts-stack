import { useCallback, useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { useRouter } from "@/routes/hooks";
import { useUserActions, useUserInfo, useUserToken } from "@/store/userStore";

const STORAGE_KEYS = {
	SESSION_ID: "dts.admin.session.id",
	SESSION_USER: "dts.admin.session.user",
	LOGOUT_TS: "dts.admin.session.logoutTs",
	TOKEN_SYNC: "dts.admin.session.tokenSync",
} as const;

const genId = () => Math.random().toString(36).slice(2) + Date.now().toString(36);

function broadcastTokenSync(tokens: Record<string, unknown>) {
	try {
		localStorage.setItem(STORAGE_KEYS.TOKEN_SYNC, JSON.stringify({ ...tokens, ts: Date.now() }));
	} catch {}
}

export function SessionManager() {
	const router = useRouter();
	const user = useUserInfo();
	const token = useUserToken();
	const { setUserToken, clearUserInfoAndToken } = useUserActions();

	const tabIdRef = useRef<string>(genId());
	const mySessionIdRef = useRef<string | null>(null);
	const logoutInProgressRef = useRef(false);

	const triggerLogout = useCallback((reason: "session-expired" | "session-conflict") => {
		if (logoutInProgressRef.current) return;
		logoutInProgressRef.current = true;
		if (reason === "session-conflict") {
			toast.error("账号已在其他位置登录，本会话已退出", { id: "session-conflict" });
		} else {
			toast.error("会话已过期，请重新登录", { id: "session-expired" });
		}
		clearUserInfoAndToken();
		localStorage.setItem(STORAGE_KEYS.LOGOUT_TS, String(Date.now()));
		router.replace("/auth/login");
	}, [clearUserInfoAndToken, router]);

	const isLoggedIn = useMemo(() => Boolean(token?.accessToken), [token?.accessToken]);
	const loginName = user?.username || user?.email || "";

	// Establish or update session marker on login
	useEffect(() => {
		if (!isLoggedIn) {
			mySessionIdRef.current = null;
			logoutInProgressRef.current = false;
			return;
		}
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
			if (e.key === STORAGE_KEYS.TOKEN_SYNC && e.newValue && isLoggedIn) {
				try {
					const synced = JSON.parse(e.newValue);
					if (synced?.accessToken && synced?.refreshToken) {
						setUserToken({
							accessToken: synced.accessToken,
							refreshToken: synced.refreshToken,
						});
					}
				} catch {}
				return;
			}
			if (e.key === STORAGE_KEYS.SESSION_ID) {
				const newId = e.newValue;
				if (isLoggedIn && newId && newId !== mySessionIdRef.current && !logoutInProgressRef.current) {
					triggerLogout("session-conflict");
				}
			}
			if (e.key === STORAGE_KEYS.LOGOUT_TS && e.newValue) {
				if (isLoggedIn && !logoutInProgressRef.current) {
					triggerLogout("session-conflict");
				}
			}
		};
		window.addEventListener("storage", onStorage);
		return () => window.removeEventListener("storage", onStorage);
	}, [isLoggedIn, setUserToken, triggerLogout]);

	useEffect(() => {
		if (!isLoggedIn || !token?.accessToken || !token?.refreshToken || logoutInProgressRef.current) return;
		broadcastTokenSync({
			accessToken: token.accessToken,
			refreshToken: token.refreshToken,
		});
	}, [isLoggedIn, token?.accessToken, token?.refreshToken]);

	return null;
}

export default SessionManager;
