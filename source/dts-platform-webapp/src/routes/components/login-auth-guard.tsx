import { useCallback, useEffect, useRef, useState } from "react";
import { isWithinLoginProbeGrace } from "@/api/apiClient";
import { getPortalSessionStatus } from "@/api/platformApi";
import menuService from "@/api/services/menuService";
import { GLOBAL_CONFIG } from "@/global-config";
import useUserStore, { useUserInfo, useUserToken } from "@/store/userStore";
import { LOGIN_ROUTE, resolveCurrentAppPath, resolveLoginHref } from "../constants";
import { useRouter } from "../hooks";

/** Decode JWT exp claim. Returns expiry in ms or null if not a valid JWT. */
function decodeJwtExp(token?: string): number | null {
	if (!token) return null;
	try {
		const parts = token.split(".");
		if (parts.length < 2) return null;
		let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
		while (payload.length % 4 !== 0) payload += "=";
		const json = atob(payload);
		const obj = JSON.parse(json);
		return typeof obj?.exp === "number" ? obj.exp * 1000 : null;
	} catch {
		return null;
	}
}

/** Check whether a token is definitely expired based on its own JWT exp claim. */
function isTokenExpired(token?: string): boolean {
	if (!token) return true;
	// Dev tokens are not JWTs; treat them as always valid.
	if (token.startsWith("dev-access-")) return false;
	const exp = decodeJwtExp(token);
	if (exp !== null) {
		return Date.now() > exp - 10_000;
	}
	// Opaque platform tokens are owned by the backend portal session.
	return false;
}

function requiresBackendSessionValidation(token?: string): boolean {
	if (!token) return false;
	if (token.startsWith("dev-access-")) return false;
	return decodeJwtExp(token) === null;
}

type Props = {
	children: React.ReactNode;
};
export default function LoginAuthGuard({ children }: Props) {
	const router = useRouter();
	const { accessToken } = useUserToken();
	const { roles = [] } = useUserInfo();
	const [sessionChecked, setSessionChecked] = useState(false);
	const [sessionAuthenticated, setSessionAuthenticated] = useState(false);
	// 对明确的 EXPIRED / CONCURRENT / LOGOUT 立即失效；
	// 只有网络抖动或无原因的 authenticated=false 才走 2 次确认阈值。
	const failCountRef = useRef(0);

	const needsBackendSessionCheck = requiresBackendSessionValidation(accessToken);

	const forceLogout = useCallback(() => {
		useUserStore.getState().actions.clearUserInfoAndToken();
		window.location.replace(resolveLoginHref(resolveCurrentAppPath()));
	}, []);

	const verifyBackendSession = useCallback(async () => {
		if (!accessToken || isTokenExpired(accessToken) || !needsBackendSessionCheck) {
			setSessionAuthenticated(Boolean(accessToken) && !isTokenExpired(accessToken));
			setSessionChecked(true);
			return;
		}
		// 登录后 grace window：portal_session 刚 save 可能对新事务不可见，直接信任本地 token。
		// 周期探活仍会在窗口外命中，不影响异地登录 / session 失效感知。
		if (isWithinLoginProbeGrace()) {
			setSessionAuthenticated(true);
			setSessionChecked(true);
			failCountRef.current = 0;
			return;
		}
		try {
			const currentAccessToken = useUserStore.getState().userToken?.accessToken || accessToken;
			const status = await getPortalSessionStatus(currentAccessToken);
			const authenticated = Boolean(status?.authenticated);
			if (authenticated) {
				failCountRef.current = 0;
				setSessionAuthenticated(true);
				setSessionChecked(true);
				return;
			}
			const reason = (status as any)?.reason;
			const isDefinitiveInactive =
				reason === "CONCURRENT" ||
				reason === "EXPIRED" ||
				reason === "LOGOUT" ||
				(status as any)?.remainingSeconds === 0;
			if (isDefinitiveInactive) {
				setSessionAuthenticated(false);
				setSessionChecked(true);
				forceLogout();
				return;
			}
			failCountRef.current += 1;
			if (failCountRef.current >= 2) {
				setSessionAuthenticated(false);
				setSessionChecked(true);
				forceLogout();
				return;
			}
			console.warn(
				`[LoginAuthGuard] backend session authenticated=false (attempt ${failCountRef.current}/2), 等待下次确认`,
			);
			// 单次失败不切换 sessionAuthenticated，避免页面闪烁或拒绝渲染。
			setSessionAuthenticated(true);
			setSessionChecked(true);
		} catch (err) {
			// Backend restart / container replacement can make the status probe temporarily fail.
			// Keep the local session and let the periodic probe recover; only explicit inactive
			// statuses are allowed to force logout.
			console.warn("[LoginAuthGuard] backend session probe unavailable, preserving local session", err);
			setSessionAuthenticated(true);
			setSessionChecked(true);
		}
	}, [accessToken, forceLogout, needsBackendSessionCheck]);

	const check = useCallback(() => {
		if (!accessToken || isTokenExpired(accessToken)) {
			console.warn("[LoginAuthGuard] redirect: no token or expired", {
				hasToken: !!accessToken,
				expired: accessToken ? isTokenExpired(accessToken) : "N/A",
			});
			if (accessToken) {
				useUserStore.getState().actions.clearUserInfoAndToken();
			}
			router.replace(LOGIN_ROUTE);
			return;
		}
		if (needsBackendSessionCheck) {
			if (!sessionChecked) {
				return;
			}
			if (!sessionAuthenticated) {
				console.warn("[LoginAuthGuard] redirect: backend session inactive");
				forceLogout();
				return;
			}
		}
		const expandSynonyms = (list: string[]): Set<string> => {
			const set = new Set<string>((list || []).map((r) => String(r || "").toUpperCase()));
			if (set.has("SYSADMIN")) set.add("ROLE_SYS_ADMIN");
			if (set.has("AUTHADMIN")) set.add("ROLE_AUTH_ADMIN");
			if (set.has("AUDITADMIN")) set.add("ROLE_SECURITY_AUDITOR");
			if (set.has("SECURITYAUDITOR")) set.add("ROLE_SECURITY_AUDITOR");
			if (set.has("OPADMIN")) set.add("ROLE_OP_ADMIN");
			return set;
		};
		const FE_GUARD_ENABLED = String(import.meta.env.VITE_ENABLE_FE_GUARD ?? "true").toLowerCase() === "true";
		if (FE_GUARD_ENABLED) {
			const allowed = Array.isArray(GLOBAL_CONFIG.allowedLoginRoles) ? GLOBAL_CONFIG.allowedLoginRoles : [];
			const allowedSet = expandSynonyms(allowed);
			const roleSet = expandSynonyms(roles as string[]);
			if (allowedSet.size > 0 && !Array.from(roleSet).some((r) => allowedSet.has(r))) {
				console.warn("[LoginAuthGuard] redirect: role not allowed", { allowed: [...allowedSet], user: [...roleSet] });
				router.replace(LOGIN_ROUTE);
				return;
			}
			// Defense-in-depth: explicitly forbid admin-console roles on platform
			if (roleSet.has("ROLE_SYS_ADMIN") || roleSet.has("ROLE_AUTH_ADMIN") || roleSet.has("ROLE_SECURITY_AUDITOR")) {
				console.warn("[LoginAuthGuard] redirect: admin role on platform", { roles: [...roleSet] });
				router.replace(LOGIN_ROUTE);
			}
		}
	}, [router, accessToken, roles, needsBackendSessionCheck, sessionChecked, sessionAuthenticated, forceLogout]);

	useEffect(() => {
		if (!needsBackendSessionCheck) {
			setSessionAuthenticated(Boolean(accessToken) && !isTokenExpired(accessToken));
			setSessionChecked(true);
			return;
		}
		setSessionChecked(false);
		void verifyBackendSession();
	}, [accessToken, needsBackendSessionCheck, verifyBackendSession]);

	useEffect(() => {
		check();
	}, [check]);

	const verifyRef = useRef(verifyBackendSession);
	verifyRef.current = verifyBackendSession;

	// Periodic auth/session check — catches backend portal session expiry while the page is idle.
	useEffect(() => {
		if (!accessToken) return;
		const timer = window.setInterval(() => {
			const currentToken = useUserStore.getState().userToken;
			if (isTokenExpired(currentToken?.accessToken)) {
				useUserStore.getState().actions.clearUserInfoAndToken();
				window.location.replace(resolveLoginHref(resolveCurrentAppPath()));
				return;
			}
			if (requiresBackendSessionValidation(currentToken?.accessToken)) {
				void verifyRef.current();
			}
		}, 30_000);
		return () => window.clearInterval(timer);
	}, [accessToken]);

	// Ensure menus reflect the current identity. Reload on token change even if a previous menu exists.
	// This fixes a stale-menu issue when switching accounts without a full page reload.
	useEffect(() => {
		if (accessToken && !accessToken.startsWith("dev-access-")) {
			menuService.getMenuTree().catch(() => {
				/* ignore */
			});
		}
	}, [accessToken]);

	// Block rendering if the token is missing or expired — prevents dashboard flash before redirect.
	if (!accessToken || isTokenExpired(accessToken)) {
		return null;
	}

	if (needsBackendSessionCheck && !sessionChecked) {
		return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">会话校验中...</div>;
	}

	if (needsBackendSessionCheck && !sessionAuthenticated) {
		return null;
	}

	return <>{children}</>;
}
