import { Navigate, useSearchParams } from "react-router";
import QualityRoutePage from "@/features/data-quality/QualityRoutePage";
import { legacyQualityRedirect } from "@/features/data-quality/qualityRoutes";

export default function QualityRulesPage() {
	const [searchParams] = useSearchParams();
	const legacyTarget = legacyQualityRedirect(searchParams);
	if (legacyTarget) return <Navigate to={legacyTarget} replace />;
	return <QualityRoutePage routeKey="overview" />;
}
