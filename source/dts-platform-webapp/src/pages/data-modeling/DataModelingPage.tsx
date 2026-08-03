import { useLocation } from "react-router";
import { resolveDataModelingRoute } from "./navigation";
import { DataModelingSurface } from "./prototype/DataModelingSurface";
import "./data-modeling.css";

export default function DataModelingPage() {
	const location = useLocation();
	return <DataModelingSurface route={resolveDataModelingRoute(location.pathname)} />;
}
