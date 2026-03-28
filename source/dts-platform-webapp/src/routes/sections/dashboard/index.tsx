import { lazy, Suspense, useEffect } from "react";
import { Navigate, type RouteObject, useLocation } from "react-router";
import { GLOBAL_CONFIG } from "@/global-config";
import { LineLoading } from "@/components/loading";
import DashboardLayout from "@/layouts/dashboard";
import LoginAuthGuard from "@/routes/components/login-auth-guard";
import { useRouter } from "@/routes/hooks";
import { DynamicMenuResolver } from "./dynamic-resolver";
import { STATIC_DASHBOARD_ROUTES } from "./static-routes";

const WorkbenchPage = lazy(() => import("@/pages/workbench"));

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
				path: "workbench",
				element: (
					<Suspense fallback={<LineLoading />}>
						<WorkbenchPage />
					</Suspense>
				),
			},
			{
				path: "dashboard",
				children: [
					{ index: true, element: <Navigate to="workbench" replace /> },
					{
						path: "workbench",
						element: (
							<Suspense fallback={<LineLoading />}>
								<WorkbenchPage />
							</Suspense>
						),
					},
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
	const fallbackPath = GLOBAL_CONFIG.defaultRoute || "/portal";

	useEffect(() => {
		if (fallbackPath && location.pathname !== fallbackPath) {
			router.replace(fallbackPath);
		}
	}, [fallbackPath, router, location.pathname]);

	return null;
}
