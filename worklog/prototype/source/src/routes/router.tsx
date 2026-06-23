import { lazy } from "react";
import { createBrowserRouter, Navigate } from "react-router";
import { AppShell } from "@/shell/AppShell";

/**
 * 路由：部门门户 + 黄金主线 4 阶段 + 平台旁路区。
 * 各页 React.lazy 懒加载 —— 把 reactflow（集成/血缘）等重依赖从首屏拆出。
 */
const DepartmentPortal = lazy(() => import("@/stages/portal/DepartmentPortal").then((m) => ({ default: m.DepartmentPortal })));
const ConnectStage = lazy(() => import("@/stages/connect/ConnectStage").then((m) => ({ default: m.ConnectStage })));
const IntegrateStage = lazy(() => import("@/stages/integrate/IntegrateStage").then((m) => ({ default: m.IntegrateStage })));
const AssetsStage = lazy(() => import("@/stages/assets/AssetsStage").then((m) => ({ default: m.AssetsStage })));
const MetricsStage = lazy(() => import("@/stages/metrics/MetricsStage").then((m) => ({ default: m.MetricsStage })));
const ServeStage = lazy(() => import("@/platform/ServeStage").then((m) => ({ default: m.ServeStage })));
const GovernStage = lazy(() => import("@/platform/GovernStage").then((m) => ({ default: m.GovernStage })));
const SecurityStage = lazy(() => import("@/platform/SecurityStage").then((m) => ({ default: m.SecurityStage })));
const OpsStage = lazy(() => import("@/platform/OpsStage").then((m) => ({ default: m.OpsStage })));
const SettingsStage = lazy(() => import("@/platform/SettingsStage").then((m) => ({ default: m.SettingsStage })));

export const router = createBrowserRouter([
	{
		path: "/",
		element: <AppShell />,
		children: [
			{ index: true, element: <Navigate to="/portal" replace /> },
			{ path: "portal", element: <DepartmentPortal /> },
			{ path: "connect", element: <ConnectStage /> },
			{ path: "integrate", element: <IntegrateStage /> },
			{ path: "assets", element: <AssetsStage /> },
			{ path: "metrics", element: <MetricsStage /> },
			{ path: "platform/serve", element: <ServeStage /> },
			{ path: "platform/govern", element: <GovernStage /> },
			{ path: "platform/security", element: <SecurityStage /> },
			{ path: "platform/ops", element: <OpsStage /> },
			{ path: "platform/settings", element: <SettingsStage /> },
			{ path: "*", element: <Navigate to="/portal" replace /> },
		],
	},
]);
