import { Navigate, useSearchParams } from "react-router";

export default function QualityReportPage() {
	const [searchParams] = useSearchParams();
	const params = new URLSearchParams(searchParams);
	params.set("tab", "report");
	return <Navigate to={`/governance/rules?${params.toString()}`} replace />;
}
