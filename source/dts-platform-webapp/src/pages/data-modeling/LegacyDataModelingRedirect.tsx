import { Navigate, useLocation } from "react-router";
import { metricsServiceHrefFromPlatformLocation } from "@/routes/sections/dashboard/metricsServiceRoutes";

const LEGACY_WORKSPACE_MODULES: Record<string, string> = {
	home: "/data-modeling/home/workspace",
	planning: "/data-modeling/planning/business-categories",
	standards: "/data-modeling/standards/fields",
	models: "/data-modeling/dimensions/workbench",
	metrics: "/data-modeling/metrics/atomic",
	tools: "/data-modeling/tools/toolbox",
	graph: "/data-modeling/graphs/models",
};

export function legacyDataModelingTarget(pathname: string, search: string, hash = "") {
	if (pathname === "/modeling/workbench") {
		const module = new URLSearchParams(search).get("module") || "home";
		return LEGACY_WORKSPACE_MODULES[module] || LEGACY_WORKSPACE_MODULES.home;
	}
	if (pathname === "/studio/modeling") return "/data-modeling/home/workspace";
	if (pathname.startsWith("/studio/modeling/warehouse-planning")) {
		return "/data-modeling/planning/business-categories";
	}
	if (pathname.startsWith("/studio/modeling/standards")) return "/data-modeling/standards/fields";
	if (pathname.startsWith("/studio/modeling/data-metrics")) return "/data-modeling/metrics/atomic";
	if (pathname === "/studio/projects") return "/data-modeling/planning/spaces";
	if (pathname === "/modeling/plans" || pathname.startsWith("/modeling/plans/")) {
		return "/data-modeling/planning/spaces";
	}
	if (pathname === "/modeling/semantic/subjects") return "/data-modeling/planning/subjects";
	if (pathname === "/bi/metrics") return "/data-modeling/metrics/atomic";
	if (pathname === "/modeling/metric-workbench" || pathname === "/modeling/semantic/metrics") {
		return "/data-modeling/metrics/atomic";
	}
	if (pathname.startsWith("/modeling/semantic-center") || pathname === "/bi/semantic-modeling") {
		return metricsServiceHrefFromPlatformLocation(pathname, search, hash);
	}
	if (pathname === "/modeling/semantic/publish" || pathname === "/modeling/semantic/runs") {
		return "/data-modeling/home/workspace";
	}
	if (pathname === "/studio/low-code-development") return "/data-modeling/home/workspace";
	return "/data-modeling/dimensions/workbench";
}

export default function LegacyDataModelingRedirect() {
	const location = useLocation();
	return <Navigate replace to={legacyDataModelingTarget(location.pathname, location.search, location.hash)} />;
}
