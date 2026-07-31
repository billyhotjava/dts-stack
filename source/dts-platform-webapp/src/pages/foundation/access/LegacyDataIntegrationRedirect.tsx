import { Navigate, useLocation, useParams } from "react-router";

const newTaskSearch = (search: string) => {
	const kind = new URLSearchParams(search).get("kind");
	return kind === "database" || kind === "api" || kind === "file" ? `?kind=${kind}` : "";
};

export default function LegacyDataIntegrationRedirect() {
	const location = useLocation();
	const { id } = useParams<{ id?: string }>();

	if (location.pathname.endsWith("/new")) {
		return <Navigate to={`/foundation/data-sources/access/new${newTaskSearch(location.search)}`} replace />;
	}

	if (id) {
		const query = new URLSearchParams();
		if (location.pathname.endsWith("/executions")) query.set("tab", "history");
		if (location.pathname.endsWith("/edit")) query.set("mode", "edit");
		const search = query.toString();
		return (
			<Navigate
				to={`/foundation/data-sources/access/${encodeURIComponent(id)}${search ? `?${search}` : ""}`}
				replace
			/>
		);
	}

	return <Navigate to="/foundation/data-sources" replace />;
}
