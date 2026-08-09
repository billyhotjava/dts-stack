import { Navigate, useLocation } from "react-router";
import { legacySubjectAreasTarget } from "./navigation";

export default function LegacySubjectAreasRedirect() {
	const location = useLocation();
	return <Navigate replace to={legacySubjectAreasTarget(location.search, location.hash)} />;
}
