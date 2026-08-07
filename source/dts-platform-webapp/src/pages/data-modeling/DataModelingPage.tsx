import { useLocation } from "react-router";
import { resolveDataModelingRoute } from "./navigation";
import { DataModelingSurface } from "./prototype/DataModelingSurface";
import "./data-modeling.css";

export default function DataModelingPage() {
	const location = useLocation();
	return (
		<div data-testid="data-modeling-page">
			<DataModelingSurface route={resolveDataModelingRoute(location.pathname)} />
		</div>
	);
}
