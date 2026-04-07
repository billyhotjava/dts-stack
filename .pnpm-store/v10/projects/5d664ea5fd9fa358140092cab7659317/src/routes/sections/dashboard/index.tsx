import { useEffect, useMemo } from "react";
import { Navigate, useNavigate, type RouteObject } from "react-router";
import { GLOBAL_CONFIG } from "@/global-config";
import DashboardLayout from "@/layouts/dashboard";
import { useFilteredNavData } from "@/layouts/dashboard/nav";
import LoginAuthGuard from "@/routes/components/login-auth-guard";
import { flattenTrees } from "@/utils/tree";
import { getBackendDashboardRoutes } from "./backend";
import { getFrontendDashboardRoutes } from "./frontend";
import { LineLoading } from "@/components/loading";
import { useUserInfo } from "@/store/userStore";

const getRoutes = (): RouteObject[] => {
	if (GLOBAL_CONFIG.routerMode === "frontend") {
		return getFrontendDashboardRoutes();
	}
	return getBackendDashboardRoutes();
};

export const resolveHomePathForRoles = (roles: string[]): string => {
	const normalized = (roles || []).map((role) =>
		String(role || "")
			.trim()
			.toUpperCase(),
	);
	const hasRole = (needle: string) => normalized.includes(needle);
	if (
		hasRole("AUDITADMIN") ||
		hasRole("ROLE_SECURITY_AUDITOR") ||
		hasRole("SECURITYAUDITOR") ||
		hasRole("ROLE_AUDITOR_ADMIN") ||
		hasRole("ROLE_AUDIT_ADMIN")
	) {
		return "/admin/audit";
	}
	if (hasRole("AUTHADMIN") || hasRole("ROLE_AUTH_ADMIN")) {
		return "/admin/approval";
	}
	if (hasRole("SYSADMIN") || hasRole("ROLE_SYS_ADMIN")) {
		return "/admin/my-changes";
	}
	return GLOBAL_CONFIG.defaultRoute;
};

function DashboardIndexRedirect() {
	const navigate = useNavigate();
	const filteredNavData = useFilteredNavData();
	const userInfo = useUserInfo();
	const normalizedRoles = useMemo(() => {
		const raw = Array.isArray(userInfo?.roles) ? userInfo?.roles : [];
		return raw
			.map((role) =>
				String(role || "")
					.trim()
					.toUpperCase(),
			)
			.filter(Boolean);
	}, [userInfo?.roles]);

	const fallbackPath = useMemo(() => resolveHomePathForRoles(normalizedRoles), [normalizedRoles]);

	const accessiblePaths = useMemo(() => {
		const paths: string[] = [];
		filteredNavData.forEach((group) => {
			const items = flattenTrees(group.items);
			items.forEach((item: any) => {
				if (item?.path) paths.push(item.path);
			});
		});
		return paths;
	}, [filteredNavData]);

	useEffect(() => {
		if (!filteredNavData.length && !accessiblePaths.length && !fallbackPath) return;
		const defaultRoute = GLOBAL_CONFIG.defaultRoute;
		const candidate =
			(accessiblePaths.includes(defaultRoute) && defaultRoute) ||
			(accessiblePaths.length ? accessiblePaths[0] : null) ||
			fallbackPath;
		if (!candidate) {
			navigate("/403", { replace: true });
			return;
		}
		navigate(candidate, { replace: true });
	}, [accessiblePaths, filteredNavData, fallbackPath, navigate]);

	if (!filteredNavData.length && !accessiblePaths.length && !fallbackPath) {
		return (
			<div className="flex h-full min-h-40 items-center justify-center">
				<LineLoading />
			</div>
		);
	}

	const target = accessiblePaths[0] ?? fallbackPath ?? GLOBAL_CONFIG.defaultRoute;
	return <Navigate to={target} replace />;
}

export const dashboardRoutes: RouteObject[] = [
	{
		element: (
			<LoginAuthGuard>
				<DashboardLayout />
			</LoginAuthGuard>
		),
		children: [{ index: true, element: <DashboardIndexRedirect /> }, ...getRoutes()],
	},
];
