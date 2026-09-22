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
import { useUserInfo, useUserToken } from "@/store/userStore";

const getRoutes = (): RouteObject[] => {
	if (GLOBAL_CONFIG.routerMode === "frontend") {
		return getFrontendDashboardRoutes();
	}
	return getBackendDashboardRoutes();
};

export { resolveHomePathForRoles } from "./home-path";
import { resolveHomePathForRoles } from "./home-path";

function DashboardIndexRedirect() {
	const navigate = useNavigate();
	const filteredNavData = useFilteredNavData();
	const userInfo = useUserInfo();
	const { accessToken } = useUserToken();
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
		if (!accessToken) return;
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
	}, [accessToken, accessiblePaths, filteredNavData, fallbackPath, navigate]);

	// 没 token 时不再根据 localStorage 里残留角色去推受保护页面；交给 LoginAuthGuard 去登录页
	if (!accessToken) {
		return <Navigate to="/auth/login" replace />;
	}

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
