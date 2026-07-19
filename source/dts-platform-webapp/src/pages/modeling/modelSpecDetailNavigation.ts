export const MODEL_SPEC_DETAIL_TABS = ["design", "fields", "standards"] as const;

export type ModelSpecDetailTab = (typeof MODEL_SPEC_DETAIL_TABS)[number];

const isModelSpecDetailTab = (value: string | null): value is ModelSpecDetailTab =>
	MODEL_SPEC_DETAIL_TABS.some((tab) => tab === value);

export const resolveModelSpecDetailTab = (searchParams: URLSearchParams): ModelSpecDetailTab => {
	const requested = searchParams.get("tab");
	return isModelSpecDetailTab(requested) ? requested : "design";
};

export const modelSpecDetailPath = (modelSpecId: string, tab: ModelSpecDetailTab, planId?: string | null): string => {
	const params = new URLSearchParams({ tab });
	const normalizedPlanId = planId?.trim();
	if (normalizedPlanId) params.set("planId", normalizedPlanId);
	return `/modeling/models/${encodeURIComponent(modelSpecId.trim())}?${params.toString()}`;
};

export const modelSpecPlanModelsPath = (planId?: string | null): string => {
	const normalizedPlanId = planId?.trim();
	if (!normalizedPlanId) return "/modeling/models";
	const params = new URLSearchParams({ planId: normalizedPlanId });
	return `/modeling/models?${params.toString()}`;
};
