import { useCallback, useEffect, useRef } from "react";
import { decodeJwtExp } from "@dts-session-core/token";
import { readStorageValue } from "@dts-session-core/storage";
import { currentRoutePath, redirectToLoginWithReturn } from "@/auth/session-auth";
import { ADMIN_LEGACY_SESSION_KEYS, ADMIN_SESSION_KEYS } from "@/auth/session-keys";
import useUserStore, { useUserInfo, useUserToken } from "@/store/userStore";
import { useRouter } from "../hooks";
import { GLOBAL_CONFIG } from "@/global-config";

function isTokenExpired(token?: string): boolean {
	if (!token) return true;
	const exp = decodeJwtExp(token);
	if (exp === null) return false;
	return Date.now() > exp - 10_000;
}

function isSessionIdle(): boolean {
	try {
		const stored = readStorageValue(ADMIN_SESSION_KEYS.lastActivity, ADMIN_LEGACY_SESSION_KEYS.lastActivity, localStorage);
		if (!stored) return false;
		const lastActivity = Number(stored);
		if (!(lastActivity > 0)) return false;
		const timeoutMinutes = Math.max(
			1,
			Number(import.meta.env.VITE_SESSION_TIMEOUT_MINUTES ?? import.meta.env.VITE_ADMIN_SESSION_TIMEOUT ?? "10"),
		);
		return Date.now() - lastActivity > timeoutMinutes * 60 * 1000;
	} catch {
		return false;
	}
}

type Props = {
	children: React.ReactNode;
};

export default function LoginAuthGuard({ children }: Props) {
	const router = useRouter();
	const { accessToken } = useUserToken();
	const { roles = [] } = useUserInfo();

	const check = useCallback(() => {
		if (!accessToken || isTokenExpired(accessToken) || isSessionIdle()) {
			if (accessToken) {
				useUserStore.getState().actions.clearUserInfoAndToken();
			}
			// Use React Router soft navigation (no page reload) — we're inside a component
			// and have router context. The 30s timer below uses redirectToLoginWithReturn()
			// (hard navigation) because it runs outside the render cycle.
			const returnUrl = encodeURIComponent(currentRoutePath());
			router.replace(`/auth/login?redirect=${returnUrl}`);
			return;
		}
		const expandSynonyms = (list: string[]): Set<string> => {
			const set = new Set<string>((list || []).map((r) => String(r || "").toUpperCase()));
			if (set.has("SYSADMIN") || set.has("SYS_ADMIN")) set.add("ROLE_SYS_ADMIN");
			if (set.has("AUTHADMIN") || set.has("AUTH_ADMIN") || set.has("IAM_ADMIN")) set.add("ROLE_AUTH_ADMIN");
			if (set.has("AUDITADMIN") || set.has("AUDIT_ADMIN")) set.add("ROLE_SECURITY_AUDITOR");
			if (set.has("SECURITYAUDITOR") || set.has("SECURITY_AUDITOR")) set.add("ROLE_SECURITY_AUDITOR");
			if (set.has("OPADMIN") || set.has("OP_ADMIN")) set.add("ROLE_OP_ADMIN");
			return set;
		};
		const feGuardEnabled = String(import.meta.env.VITE_ENABLE_FE_GUARD || "false").toLowerCase() === "true";
		if (feGuardEnabled) {
			const allowed = Array.isArray(GLOBAL_CONFIG.allowedLoginRoles) ? GLOBAL_CONFIG.allowedLoginRoles : [];
			const allowedSet = expandSynonyms(allowed);
			const roleSet = expandSynonyms(roles as string[]);
			if (allowedSet.size > 0 && !Array.from(roleSet).some((role) => allowedSet.has(role))) {
				router.replace("/auth/login");
				return;
			}
			if (roleSet.has("ROLE_OP_ADMIN")) {
				router.replace("/auth/login");
			}
		}
	}, [accessToken, roles, router]);

	useEffect(() => {
		check();
	}, [check]);

	const checkRef = useRef(check);
	checkRef.current = check;
	useEffect(() => {
		if (!accessToken) return;
		const timer = window.setInterval(() => {
			if (isTokenExpired(accessToken) || isSessionIdle()) {
				useUserStore.getState().actions.clearUserInfoAndToken();
				redirectToLoginWithReturn();
			}
		}, 30_000);
		return () => window.clearInterval(timer);
	}, [accessToken]);

	if (!accessToken || isTokenExpired(accessToken) || isSessionIdle()) {
		return null;
	}

	return <>{children}</>;
}
