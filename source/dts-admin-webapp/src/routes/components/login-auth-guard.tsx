import { useEffect } from "react";
import { canAccessProtectedRoute, shouldBlockWhileSessionBootstraps } from "@/auth/session-state";
import { redirectToLoginWithReturn } from "@/auth/session-auth";
import { GLOBAL_CONFIG } from "@/global-config";
import { usePortalSession, useUserInfo } from "@/store/userStore";
import { useRouter } from "../hooks";

type Props = {
	children: React.ReactNode;
};

const LOGIN_ROUTE = "/auth/login";

const expandSynonyms = (roles: string[]): Set<string> => {
	const set = new Set<string>((roles || []).map((role) => String(role || "").toUpperCase()));
	if (set.has("SYSADMIN") || set.has("SYS_ADMIN")) set.add("ROLE_SYS_ADMIN");
	if (set.has("AUTHADMIN") || set.has("AUTH_ADMIN") || set.has("IAM_ADMIN")) set.add("ROLE_AUTH_ADMIN");
	if (set.has("AUDITADMIN") || set.has("AUDIT_ADMIN") || set.has("SECURITYAUDITOR") || set.has("SECURITY_AUDITOR")) {
		set.add("ROLE_SECURITY_AUDITOR");
	}
	if (set.has("OPADMIN") || set.has("OP_ADMIN")) set.add("ROLE_OP_ADMIN");
	return set;
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
		const FE_GUARD_ENABLED = String(import.meta.env.VITE_ENABLE_FE_GUARD ?? "true").toLowerCase() === "true";
		if (FE_GUARD_ENABLED) {
			const allowed = Array.isArray(GLOBAL_CONFIG.allowedLoginRoles) ? GLOBAL_CONFIG.allowedLoginRoles : [];
			const allowedSet = expandSynonyms(allowed);
			const roleSet = expandSynonyms(roles as string[]);
			if (allowedSet.size > 0 && !Array.from(roleSet).some((role) => allowedSet.has(role))) {
				router.replace(LOGIN_ROUTE);
				return;
			}
			if (roleSet.has("ROLE_OP_ADMIN")) {
				router.replace(LOGIN_ROUTE);
			}
		}
	}, [roles, router, session]);

	if (shouldBlockWhileSessionBootstraps(session)) {
		return null;
	}

	if (!canAccessProtectedRoute(session)) {
		return null;
	}

	return <>{children}</>;
}
