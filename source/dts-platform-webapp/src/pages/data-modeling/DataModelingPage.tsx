import { Navigate, useLocation } from "react-router";
import { legacyPlanningArchitectureTarget } from "@/pages/data-architecture/navigation";
import { resolveDataModelingRoute } from "./navigation";
import { DataModelingSurface } from "./prototype/DataModelingSurface";
import "./data-modeling.css";

export default function DataModelingPage() {
	const location = useLocation();
	const architectureTarget = legacyPlanningArchitectureTarget(location.pathname, location.search, location.hash);
	if (architectureTarget) return <Navigate replace to={architectureTarget} />;
	if (location.pathname === "/data-modeling/standards/fields") {
		return (
			<Navigate
				replace
				to={{ pathname: "/governance/standards/elements", search: location.search, hash: location.hash }}
			/>
		);
	}
	return (
		<div data-testid="data-modeling-page">
			<DataModelingSurface route={resolveDataModelingRoute(location.pathname)} />
		</div>
	);
}
