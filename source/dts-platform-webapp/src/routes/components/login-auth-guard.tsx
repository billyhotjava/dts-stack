import { useCallback, useEffect } from "react";
import menuService from "@/api/services/menuService";
import useUserStore, { useUserInfo, useUserToken } from "@/store/userStore";
import { LOGIN_ROUTE } from "../constants";
import { useRouter } from "../hooks";
import { GLOBAL_CONFIG } from "@/global-config";

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

/** Check whether a JWT access token is expired (with 10s skew). */
function isTokenExpired(token?: string): boolean {
	if (!token) return true;
	// Dev tokens are not JWTs; treat them as always valid.
	if (token.startsWith("dev-access-")) return false;
	const exp = decodeJwtExp(token);
	if (exp === null) return false; // Opaque token; can't check locally, trust it.
	return Date.now() > exp - 10_000;
}

type Props = {
	children: React.ReactNode;
};
export default function LoginAuthGuard({ children }: Props) {
    const router = useRouter();
    const { accessToken } = useUserToken();
    const { roles = [] } = useUserInfo();

	const isLocalDevToken = (token?: string) => Boolean(token?.startsWith("dev-access-"));

    const check = useCallback(() => {
        if (!accessToken || isTokenExpired(accessToken)) {
            // Clear stale token so the user doesn't flash the dashboard on next visit.
            if (accessToken) {
                useUserStore.getState().actions.clearUserInfoAndToken();
            }
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

	// Block rendering if the token is missing or expired — prevents dashboard flash before redirect.
	if (!accessToken || isTokenExpired(accessToken)) {
		return null;
	}

	return <>{children}</>;
}
