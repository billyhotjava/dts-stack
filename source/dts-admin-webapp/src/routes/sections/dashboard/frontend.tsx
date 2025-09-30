import type { RouteObject } from "react-router";
import { Navigate } from "react-router";
import { Component } from "./utils";

export function getFrontendDashboardRoutes(): RouteObject[] {
	const frontendDashboardRoutes: RouteObject[] = [
		{ path: "workbench", element: Component("/pages/dashboard/workbench") },
		{
			path: "management",
			children: [
				{ index: true, element: <Navigate to="user" replace /> },
				{
					path: "user",
					children: [
						{ index: true, element: <Navigate to="profile" replace /> },
						{ path: "profile", element: Component("/pages/management/user/profile") },
						{ path: "account", element: Component("/pages/management/user/account") },
					],
				},
				{
					path: "system",
					children: [
						{ index: true, element: <Navigate to="user" replace /> },
						{ path: "permission", element: Component("/pages/management/system/permission") },
						{ path: "role", element: Component("/pages/management/system/role") },
						{ path: "group", element: Component("/pages/management/system/group") },
						{ path: "user", element: Component("/pages/management/system/user") },
						{ path: "user/:id", element: Component("/pages/management/system/user/detail") },
						{ path: "approval", element: Component("/pages/management/system/approval") },
						{ path: "my-changes", element: Component("/pages/management/system/my-changes") },
						{ path: "audit-log", element: Component("/pages/management/system/audit-log") },
					],
				},
			],
		},
	];
	return frontendDashboardRoutes;
}
