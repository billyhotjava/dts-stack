import type { DataModelingRoute } from "../types";
import { MetricsPage } from "./MetricsPage";
import { ModelingWorkbenchPage } from "./ModelingWorkbenchPage";
import { OverviewPage } from "./OverviewPage";
import { PlanningPage } from "./PlanningPage";
import { RelationshipGraphPage } from "./RelationshipGraphPage";
import { ReverseModelingPage } from "./ReverseModelingPage";
import { StandardsPage } from "./StandardsPage";
import { ToolsPage } from "./ToolsPage";

export function DataModelingSurface({ route }: { route: DataModelingRoute }) {
	if (route.workspace === "planning") return <PlanningPage route={route} />;
	if (route.workspace === "standards") return <StandardsPage route={route} />;
	if (route.workspace === "metrics") return <MetricsPage route={route} />;
	if (route.workspace === "tools") return <ToolsPage route={route} />;
	if (route.workspace === "graphs") return <RelationshipGraphPage route={route} />;
	if (route.workspace === "dimensions" && route.view === "reverse") return <ReverseModelingPage route={route} />;
	if (route.workspace === "dimensions") return <ModelingWorkbenchPage route={route} />;
	return <OverviewPage route={route} />;
}
