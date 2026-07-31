import { Navigate, useLocation } from "react-router";
import { resolveDataModelingRoute, retiredDataModelingHomeRedirect } from "./navigation";
import { DimensionalModelingWorkspace } from "./pages/DimensionalModelingWorkspace";
import { HomeWorkspace } from "./pages/HomeWorkspace";
import { MetricsWorkspace } from "./pages/MetricsWorkspace";
import { PlanningWorkspace } from "./pages/PlanningWorkspace";
import { RelationshipGraphWorkspace } from "./pages/RelationshipGraphWorkspace";
import { StandardsWorkspace } from "./pages/StandardsWorkspace";
import { ToolsWorkspace } from "./pages/ToolsWorkspace";
import "./data-modeling.css";

export default function DataModelingPage() {
	const location = useLocation();
	const retiredHomeRedirect = retiredDataModelingHomeRedirect(location.pathname);
	if (retiredHomeRedirect) {
		return <Navigate replace to={{ pathname: retiredHomeRedirect, search: location.search, hash: location.hash }} />;
	}
	const route = resolveDataModelingRoute(location.pathname);

	switch (route.workspace) {
		case "planning":
			return <PlanningWorkspace route={route} />;
		case "standards":
			return <StandardsWorkspace route={route} />;
		case "dimensions":
			return <DimensionalModelingWorkspace route={route} />;
		case "metrics":
			return <MetricsWorkspace route={route} />;
		case "tools":
			return <ToolsWorkspace route={route} />;
		case "graphs":
			return <RelationshipGraphWorkspace route={route} />;
		default:
			return <HomeWorkspace route={route} />;
	}
}
