import { useCallback, useEffect } from "react";
import menuService from "@/api/services/menuService";
import { useUserInfo, useUserToken } from "@/store/userStore";
import { LOGIN_ROUTE } from "../constants";
import { useRouter } from "../hooks";
import { GLOBAL_CONFIG } from "@/global-config";

type Props = {
	children: React.ReactNode;
};
export default function LoginAuthGuard({ children }: Props) {
    const router = useRouter();
    const { accessToken } = useUserToken();
    const { roles = [] } = useUserInfo();

	const isLocalDevToken = (token?: string) => Boolean(token?.startsWith("dev-access-"));

    const check = useCallback(() => {
        if (!accessToken) {
            router.replace(LOGIN_ROUTE);
            return;
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
                router.replace(LOGIN_ROUTE);
                return;
            }
            // Defense-in-depth: explicitly forbid admin-console roles on platform
            if (roleSet.has("ROLE_SYS_ADMIN") || roleSet.has("ROLE_AUTH_ADMIN") || roleSet.has("ROLE_SECURITY_AUDITOR")) {
                router.replace(LOGIN_ROUTE);
            }
        }
    }, [router, accessToken, roles]);

    useEffect(() => {
        check();
    }, [check]);

    // Ensure menus reflect the current identity. Reload on token change even if a previous menu exists.
    // This fixes a stale-menu issue when switching accounts without a full page reload.
    useEffect(() => {
        if (accessToken && !isLocalDevToken(accessToken)) {
            menuService
                .getMenuTree()
                .catch(() => {
                    /* ignore */
                });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [accessToken]);

	return <>{children}</>;
}
