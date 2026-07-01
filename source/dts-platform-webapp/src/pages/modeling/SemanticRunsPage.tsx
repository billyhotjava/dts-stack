import { Navigate } from "react-router";

export default function SemanticRunsPage() {
	return <Navigate to="/ops/instances?entryKey=DBT_RUN" replace />;
}
