import { useEffect } from "react";
import menuService from "@/api/services/menuService";
import { canAccessProtectedRoute, shouldBlockWhileSessionBootstraps } from "@/auth/session-state";
import { redirectToLoginWithReturn } from "@/auth/session-auth";
import { GLOBAL_CONFIG } from "@/global-config";
import { useUserInfo, usePortalSession } from "@/store/userStore";
import { LOGIN_ROUTE } from "../constants";
import { useRouter } from "../hooks";

type Props = {
	children: React.ReactNode;
};

export default function LoginAuthGuard({ children }: Props) {
	const router = useRouter();
	const session = usePortalSession();
	const { roles = [] } = useUserInfo();

	useEffect(() => {
		if (shouldBlockWhileSessionBootstraps(session)) {
			return;
		}
		if (!canAccessProtectedRoute(session)) {
			redirectToLoginWithReturn();
			return;
		}
		const expandSynonyms = (list: string[]): Set<string> => {
			const set = new Set<string>((list || []).map((role) => String(role || "").toUpperCase()));
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
			if (allowedSet.size > 0 && !Array.from(roleSet).some((role) => allowedSet.has(role))) {
				router.replace(LOGIN_ROUTE);
				return;
			}
			if (roleSet.has("ROLE_SYS_ADMIN") || roleSet.has("ROLE_AUTH_ADMIN") || roleSet.has("ROLE_SECURITY_AUDITOR")) {
				router.replace(LOGIN_ROUTE);
				return;
			}
		}
		menuService.getMenuTree().catch(() => undefined);
	}, [roles, router, session]);

	if (shouldBlockWhileSessionBootstraps(session)) {
		return null;
	}

	if (!canAccessProtectedRoute(session)) {
		return null;
	}

	return <>{children}</>;
}
