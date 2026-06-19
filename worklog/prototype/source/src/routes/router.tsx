import { createBrowserRouter, Navigate } from "react-router";
import { AppShell } from "@/shell/AppShell";
import { ProjectPortal } from "@/stages/portal/ProjectPortal";
import { StagePlaceholder } from "@/stages/StagePlaceholder";

/**
 * 路由：黄金主线 4 阶段 + 平台旁路区。
 * 阶段页 S1 为信息性占位，后续 sprint 逐步替换为真实页面。
 */
export const router = createBrowserRouter([
	{
		path: "/",
		element: <AppShell />,
		children: [
			{ index: true, element: <Navigate to="/portal" replace /> },
			{ path: "portal", element: <ProjectPortal /> },
			{ path: "connect", element: <StagePlaceholder areaKey="connect" /> },
			{ path: "integrate", element: <StagePlaceholder areaKey="integrate" /> },
			{ path: "assets", element: <StagePlaceholder areaKey="assets" /> },
			{ path: "metrics", element: <StagePlaceholder areaKey="metrics" /> },
			{ path: "platform/serve", element: <StagePlaceholder areaKey="serve" /> },
			{ path: "platform/govern", element: <StagePlaceholder areaKey="govern" /> },
			{ path: "platform/security", element: <StagePlaceholder areaKey="security" /> },
			{ path: "platform/ops", element: <StagePlaceholder areaKey="ops" /> },
			{ path: "platform/settings", element: <StagePlaceholder areaKey="settings" /> },
			{ path: "*", element: <Navigate to="/portal" replace /> },
		],
	},
]);
