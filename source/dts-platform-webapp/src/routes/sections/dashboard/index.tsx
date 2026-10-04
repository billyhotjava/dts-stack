import { lazy, Suspense } from "react";
import { Navigate, type RouteObject } from "react-router";
import { LineLoading } from "@/components/loading";
import DashboardLayout from "@/layouts/dashboard";
import LoginAuthGuard from "@/routes/components/login-auth-guard";
import { AuthorizedWorkbenchRoute } from "./AuthorizedWorkbenchRoute";
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
			{ index: true, element: <AuthorizedWorkbenchRoute /> },
			{
				path: "workbench",
				element: (
					<Suspense fallback={<LineLoading />}>
						<AuthorizedWorkbenchRoute>
							<WorkbenchPage />
						</AuthorizedWorkbenchRoute>
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
								<AuthorizedWorkbenchRoute>
									<WorkbenchPage />
								</AuthorizedWorkbenchRoute>
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
