import { Navigate, useLocation } from "react-router";

const positiveTaskId = (value: string | null) => (value && /^[1-9]\d*$/.test(value) ? value : "");

export default function LegacyOrchestrationRedirect() {
	const location = useLocation();
	const legacyQuery = new URLSearchParams(location.search);
	const taskId = positiveTaskId(legacyQuery.get("taskId"));

	if (taskId) {
		const query = new URLSearchParams();
		if (legacyQuery.get("tab") === "runs" || positiveTaskId(legacyQuery.get("executionId"))) {
			query.set("tab", "history");
		}
		const search = query.toString();
		return <Navigate to={`/foundation/data-sources/access/${taskId}${search ? `?${search}` : ""}`} replace />;
	}

	return <Navigate to="/foundation/data-sources" replace />;
}
