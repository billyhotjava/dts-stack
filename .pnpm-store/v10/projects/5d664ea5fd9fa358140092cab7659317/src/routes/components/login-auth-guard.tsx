import { useCallback, useEffect } from "react";
import { useUserInfo, useUserToken } from "@/store/userStore";
import { useRouter } from "../hooks";
import { GLOBAL_CONFIG } from "@/global-config";

type Props = {
	children: React.ReactNode;
};
export default function LoginAuthGuard({ children }: Props) {
	const router = useRouter();
	const { accessToken } = useUserToken();
	const { roles = [] } = useUserInfo();

	const check = useCallback(() => {
		if (!accessToken) {
			router.replace("/auth/login");
			return;
		}
		const expandSynonyms = (list: string[]): Set<string> => {
			const set = new Set<string>((list || []).map((r) => String(r || "").toUpperCase()));
			if (set.has("SYSADMIN") || set.has("SYS_ADMIN")) {
				set.add("ROLE_SYS_ADMIN");
			}
			if (set.has("AUTHADMIN") || set.has("AUTH_ADMIN") || set.has("IAM_ADMIN")) {
				set.add("ROLE_AUTH_ADMIN");
			}
			if (set.has("AUDITADMIN") || set.has("AUDIT_ADMIN")) {
				set.add("ROLE_SECURITY_AUDITOR");
			}
			if (set.has("SECURITYAUDITOR") || set.has("SECURITY_AUDITOR")) {
				set.add("ROLE_SECURITY_AUDITOR");
			}
			if (set.has("OPADMIN") || set.has("OP_ADMIN")) {
				set.add("ROLE_OP_ADMIN");
			}
			return set;
		};
		const FE_GUARD_ENABLED = String(import.meta.env.VITE_ENABLE_FE_GUARD || "false").toLowerCase() === "true";
		if (FE_GUARD_ENABLED) {
			const allowed = Array.isArray(GLOBAL_CONFIG.allowedLoginRoles) ? GLOBAL_CONFIG.allowedLoginRoles : [];
			const allowedSet = expandSynonyms(allowed);
			const roleSet = expandSynonyms(roles as string[]);
			if (allowedSet.size > 0 && !Array.from(roleSet).some((r) => allowedSet.has(r))) {
				router.replace("/auth/login");
				return;
			}
			// Defense-in-depth: explicitly forbid platform-only role on admin console
			if (roleSet.has("ROLE_OP_ADMIN")) {
				router.replace("/auth/login");
				return;
			}
		}
	}, [router, accessToken, roles]);

	useEffect(() => {
		check();
	}, [check]);

	return <>{children}</>;
}
