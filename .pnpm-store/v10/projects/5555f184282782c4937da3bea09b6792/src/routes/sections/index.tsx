import { Navigate, type RouteObject } from "react-router";
import { LOGIN_ROUTE } from "../constants";
import { authRoutes } from "./auth";
import { analyticsStandaloneRoutes } from "./analytics";
import { dashboardRoutes } from "./dashboard";
import { mainRoutes } from "./main";

export const makeRoutesSection = (): RouteObject[] => [
	{ index: true, element: <Navigate to={LOGIN_ROUTE} replace /> },
	// Auth
	...authRoutes,
	// Analytics standalone pages (full-screen designer, public share links)
	...analyticsStandaloneRoutes,
	// Dashboard (all menu-driven pages, including analytics via PATH_COMPONENT_OVERRIDES)
	...dashboardRoutes,
	// Main
	...mainRoutes,
	// No Match
	{ path: "*", element: <Navigate to="/404" replace /> },
];
