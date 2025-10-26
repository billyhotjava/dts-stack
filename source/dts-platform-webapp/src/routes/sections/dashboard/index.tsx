import { useEffect, useMemo } from "react";
import { Navigate, type RouteObject, useLocation } from "react-router";
import DashboardLayout from "@/layouts/dashboard";
import LoginAuthGuard from "@/routes/components/login-auth-guard";
import Workbench from "@/pages/dashboard/workbench";
import PersonalProfilePage from "@/pages/settings/profile";
import { DynamicMenuResolver } from "./dynamic-resolver";
import { STATIC_DASHBOARD_ROUTES } from "./static-routes";
import { useMenuStore } from "@/store/menuStore";
import useUserStore from "@/store/userStore";
import { useRouter } from "@/routes/hooks";
import { GLOBAL_CONFIG } from "@/global-config";
import { firstAccessibleMenuPath } from "@/utils/menuTree";

export const dashboardRoutes: RouteObject[] = [
	{
		element: (
			<LoginAuthGuard>
				<DashboardLayout />
			</LoginAuthGuard>
		),
		children: [
			{ index: true, element: <FallbackDashboardIndex /> },
			{
				path: "dashboard",
				children: [
					{ index: true, element: <Navigate to="workbench" replace /> },
					{ path: "workbench", element: <Workbench /> },
				],
			},
				{
					path: "settings",
					children: [
						{ index: true, element: <Navigate to="profile" replace /> },
						{ path: "profile", element: <PersonalProfilePage /> },
					],
				},
				...STATIC_DASHBOARD_ROUTES,
				{ path: "*", element: <DynamicMenuResolver /> },
		],
	},
];

function FallbackDashboardIndex() {
	const router = useRouter();
	const location = useLocation();
	const menus = useMenuStore((s) => s.menus);
	const roles = useUserStore((s) => s.userInfo.roles || []);

	const hasAdmin = useMemo(() => {
		return roles.some((r: any) => {
			const code = typeof r === "string" ? r : r?.code;
			const upper = (code || "").toString().toUpperCase();
			return upper === "ROLE_OP_ADMIN" || upper === "ROLE_SYS_ADMIN" || upper === "ROLE_ADMIN";
		});
	}, [roles]);

	const primaryMenuPath = useMemo(() => firstAccessibleMenuPath(Array.isArray(menus) ? menus : []), [menus]);
	const fallbackPath = GLOBAL_CONFIG.defaultRoute || "/dashboard/workbench";

	useEffect(() => {
		const target = primaryMenuPath || (hasAdmin ? fallbackPath : "");
		if (!target) {
			return;
		}
		if (location.pathname !== target) {
			router.replace(target);
		}
	}, [primaryMenuPath, hasAdmin, fallbackPath, router, location.pathname]);

	return null;
}
