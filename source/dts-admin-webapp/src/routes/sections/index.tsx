import { Navigate, type RouteObject } from "react-router";
import { authRoutes } from "./auth";
import { dashboardRoutes } from "./dashboard";
import { adminRoutes } from "../admin-routes";
import { mainRoutes } from "./main";

export const routesSection: RouteObject[] = [
	// Auth
	...authRoutes,
	// Dashboard
	...dashboardRoutes,
	// Admin
	...adminRoutes,
	// Main
	...mainRoutes,
	// No Match
	{ path: "*", element: <Navigate to="/404" replace /> },
];
