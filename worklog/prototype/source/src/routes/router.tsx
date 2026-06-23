import { createBrowserRouter, Navigate } from "react-router";
import { AppShell } from "@/shell/AppShell";
import { AssetsStage } from "@/stages/assets/AssetsStage";
import { ConnectStage } from "@/stages/connect/ConnectStage";
import { IntegrateStage } from "@/stages/integrate/IntegrateStage";
import { MetricsStage } from "@/stages/metrics/MetricsStage";
import { DepartmentPortal } from "@/stages/portal/DepartmentPortal";
import { GovernStage } from "@/platform/GovernStage";
import { OpsStage } from "@/platform/OpsStage";
import { SecurityStage } from "@/platform/SecurityStage";
import { ServeStage } from "@/platform/ServeStage";
import { SettingsStage } from "@/platform/SettingsStage";

/**
 * 路由：部门门户 + 黄金主线 4 阶段 + 平台旁路区。
 * 资产③/指标④ S1 仍为信息性占位，后续 sprint 替换。
 */
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
