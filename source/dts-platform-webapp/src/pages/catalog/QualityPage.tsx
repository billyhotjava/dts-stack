import { Navigate, useSearchParams } from "react-router";

/**
 * Compatibility-only route for retired catalog quality UI.
 * The standalone quality report menu owns the canonical report experience.
 */
export default function QualityPage() {
	const [searchParams] = useSearchParams();
	const query = searchParams.toString();
	return <Navigate to={`/governance/quality${query ? `?${query}` : ""}`} replace />;
}
