import { Navigate, useLocation } from "react-router";
import { buildMetricWorkbenchLocation } from "@/features/modeling/indicators/indicatorDefinitionWorkflow";

export default function IndicatorsPage() {
	const location = useLocation();
	const target = buildMetricWorkbenchLocation(location.search, location.hash);

	return <Navigate to={target || "/data-modeling/metrics/atomic"} replace />;
}
