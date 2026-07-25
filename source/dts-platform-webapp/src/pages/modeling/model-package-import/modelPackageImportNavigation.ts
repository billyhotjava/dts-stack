export const buildModelPackageImportQuery = (
	pathname: string,
	current: URLSearchParams,
	options: { open: boolean; planId?: string; runId?: string },
): string => {
	const next = new URLSearchParams(current);
	if (options.open) next.set("modelImport", "open");
	else next.delete("modelImport");
	if (options.planId) next.set("planId", options.planId);
	if (options.runId) next.set("importRunId", options.runId);
	else next.delete("importRunId");
	const query = next.toString();
	return query ? `${pathname}?${query}` : pathname;
};

export const buildImportedModelRoute = (modelSpecId: string, planId?: string): string => {
	const params = new URLSearchParams({ activeStage: "logical" });
	if (planId) params.set("planId", planId);
	return `/modeling/models/${encodeURIComponent(modelSpecId)}?${params.toString()}`;
};

export const buildImportPlanRoute = (planId: string): string =>
	`/modeling/workbench?planId=${encodeURIComponent(planId)}`;

export const buildImportRepairRoute = (planId: string, target: "categories" | "sources"): string => {
	const params = new URLSearchParams({ planId, tab: target });
	return `/modeling/plans/${encodeURIComponent(planId)}/baseline?${params.toString()}`;
};
