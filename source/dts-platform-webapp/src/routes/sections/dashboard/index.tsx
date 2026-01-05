import { useEffect } from "react";
import { Navigate, type RouteObject, useLocation } from "react-router";
import DashboardLayout from "@/layouts/dashboard";
import LoginAuthGuard from "@/routes/components/login-auth-guard";
import WorkbenchPage from "@/pages/workbench";
import BiScreensPage from "@/pages/dashboard/bi";
import ReportsPage from "@/pages/visualization/ReportsPage";
import AnalyticsPage from "@/pages/visualization/AnalyticsPage";
import ReportsManagePage from "@/pages/visualization/ReportsManagePage";
import PersonalProfilePage from "@/pages/settings/profile";
import { DynamicMenuResolver } from "./dynamic-resolver";
import { STATIC_DASHBOARD_ROUTES } from "./static-routes";
import { useRouter } from "@/routes/hooks";
import { GLOBAL_CONFIG } from "@/global-config";

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
					{ path: "workbench", element: <Navigate to="/workbench" replace /> },
					{ path: "bi", element: <BiScreensPage /> },
				],
			},
			{
				path: "visualization",
				children: [
					{ index: true, element: <Navigate to="reports" replace /> },
					{ path: "reports", element: <ReportsPage /> },
					{ path: "analytics", element: <AnalyticsPage /> },
					{ path: "reports-manage", element: <ReportsManagePage /> },
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
	const fallbackPath = GLOBAL_CONFIG.defaultRoute || "/workbench";

	useEffect(() => {
		if (fallbackPath && location.pathname !== fallbackPath) {
			router.replace(fallbackPath);
		}
	}, [fallbackPath, router, location.pathname]);

	return null;
}
