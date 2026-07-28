export type MetricWorkbenchView = "definition" | "model" | "templates" | "consumption";

const WORKBENCH_VIEWS = new Set<MetricWorkbenchView>(["definition", "model", "templates", "consumption"]);

export function resolveMetricWorkbenchView(search: string): MetricWorkbenchView {
	const params = new URLSearchParams(search);
	const requested = params.get("view") as MetricWorkbenchView | null;
	if (requested && WORKBENCH_VIEWS.has(requested)) return requested;
	return params.get("modelSpecId") || params.get("modelId") ? "model" : "definition";
}

export function buildMetricWorkbenchViewLocation(view: MetricWorkbenchView, search: string, hash = "") {
	const params = new URLSearchParams(search);
	params.set("view", view);
	return `/modeling/metric-workbench?${params.toString()}${hash}`;
}
