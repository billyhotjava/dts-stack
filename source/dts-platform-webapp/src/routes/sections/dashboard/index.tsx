import { useEffect } from "react";
import { Navigate, type RouteObject, useLocation } from "react-router";
import { GLOBAL_CONFIG } from "@/global-config";
import DashboardLayout from "@/layouts/dashboard";
import WorkbenchPage from "@/pages/workbench";
import LoginAuthGuard from "@/routes/components/login-auth-guard";
import { useRouter } from "@/routes/hooks";
import { DynamicMenuResolver } from "./dynamic-resolver";
import { STATIC_DASHBOARD_ROUTES } from "./static-routes";

export const dashboardRoutes: RouteObject[] = [
	{
		element: (
			<LoginAuthGuard>
				<DashboardLayout />
			</LoginAuthGuard>
		),
		children: [
			{ index: true, element: <FallbackDashboardIndex /> },
			{ path: "workbench", element: <WorkbenchPage /> },
			{
				path: "dashboard",
				children: [
					{ index: true, element: <Navigate to="workbench" replace /> },
					{ path: "workbench", element: <WorkbenchPage /> },
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
	const fallbackPath = GLOBAL_CONFIG.defaultRoute || "/dashboard/workbench";

	useEffect(() => {
		if (fallbackPath && location.pathname !== fallbackPath) {
			router.replace(fallbackPath);
		}
	}, [fallbackPath, router, location.pathname]);

	return null;
}
