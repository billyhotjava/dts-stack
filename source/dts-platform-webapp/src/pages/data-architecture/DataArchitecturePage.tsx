import { useCallback } from "react";
import { useSearchParams } from "react-router";
import { PlanningPage } from "@/pages/data-modeling/prototype/PlanningPage";
import { dataArchitectureRoute, resolveDataArchitectureView } from "./navigation";
import "@/pages/data-modeling/data-modeling.css";

export default function DataArchitecturePage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const view = resolveDataArchitectureView(searchParams.get("view"));
	const activeId = searchParams.get("active") || "";
	const setActiveId = useCallback(
		(next: string | null) => {
			const params = new URLSearchParams(searchParams);
			params.set("view", view);
			if (next) params.set("active", next);
			else params.delete("active");
			setSearchParams(params, { replace: true });
		},
		[searchParams, setSearchParams, view],
	);

	return (
		<div data-testid="data-architecture-page">
			<PlanningPage
				activeId={activeId}
				navigationSurface="architecture"
				onActiveChange={setActiveId}
				route={dataArchitectureRoute(view)}
				sidebarActiveView={view}
				surface="architecture"
			/>
		</div>
	);
}
