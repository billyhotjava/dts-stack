import { Navigate, useLocation } from "react-router";
import { buildMetricWorkbenchLocation } from "../modeling/indicatorDefinitionWorkflow";

export default function IndicatorsPage() {
	const location = useLocation();
	const target = buildMetricWorkbenchLocation(location.search, location.hash);

	return <Navigate to={target || "/modeling/metric-workbench"} replace />;
}
