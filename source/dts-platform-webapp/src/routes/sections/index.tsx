import { Navigate, type RouteObject } from "react-router";
import { LOGIN_ROUTE } from "../constants";
import { authRoutes } from "./auth";
import { dashboardRoutes } from "./dashboard";
import { mainRoutes } from "./main";

export const makeRoutesSection = (): RouteObject[] => [
	{ index: true, element: <Navigate to={LOGIN_ROUTE} replace /> },
	// Auth
	...authRoutes,
	// Dashboard
	...dashboardRoutes,
	// Main
	...mainRoutes,
	// No Match
	{ path: "*", element: <Navigate to="/404" replace /> },
];
