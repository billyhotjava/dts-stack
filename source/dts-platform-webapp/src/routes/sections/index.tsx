import { Suspense, lazy } from "react";
import { Navigate, type RouteObject } from "react-router";
import LoginAuthGuard from "@/routes/components/login-auth-guard";
import { LOGIN_ROUTE } from "../constants";
import { authRoutes } from "./auth";
import { dashboardRoutes } from "./dashboard";
import { mainRoutes } from "./main";

const PortalPage = lazy(() => import("@/pages/portal/PortalPage"));

export const makeRoutesSection = (): RouteObject[] => [
	{ index: true, element: <Navigate to={LOGIN_ROUTE} replace /> },
	// Auth
	...authRoutes,
	// Portal (full-screen, outside DashboardLayout)
	{
		path: "portal",
		element: (
			<LoginAuthGuard>
				<Suspense fallback={null}>
					<PortalPage />
				</Suspense>
			</LoginAuthGuard>
		),
	},
	// Dashboard
	...dashboardRoutes,
	// Main
	...mainRoutes,
	// No Match
	{ path: "*", element: <Navigate to="/404" replace /> },
];
