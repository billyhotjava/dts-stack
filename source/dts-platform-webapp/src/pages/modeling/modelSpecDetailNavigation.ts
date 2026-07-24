import type { ModelSpecType } from "./modelSpecV2Contract";

export const MODEL_SPEC_DETAIL_TABS = ["design", "fields", "standards"] as const;
export const MODEL_SPEC_DETAIL_STAGES = ["logical", "implementation", "physical"] as const;

export type ModelSpecDetailTab = (typeof MODEL_SPEC_DETAIL_TABS)[number];
export type ModelSpecDetailStage = (typeof MODEL_SPEC_DETAIL_STAGES)[number];

const isModelSpecDetailTab = (value: string | null): value is ModelSpecDetailTab =>
	MODEL_SPEC_DETAIL_TABS.some((tab) => tab === value);

const isModelSpecDetailStage = (value: string | null): value is ModelSpecDetailStage =>
	MODEL_SPEC_DETAIL_STAGES.some((stage) => stage === value);

const legacyTabStage: Record<ModelSpecDetailTab | "release", ModelSpecDetailStage> = {
	design: "logical",
	fields: "logical",
	standards: "logical",
	release: "physical",
};

const legacyTabToStage = (tab: ModelSpecDetailTab): ModelSpecDetailStage => legacyTabStage[tab];

export const resolveModelSpecDetailTab = (searchParams: URLSearchParams): ModelSpecDetailTab => {
	const requested = searchParams.get("tab");
	return isModelSpecDetailTab(requested) ? requested : "design";
};

export const resolveModelSpecDetailStage = (searchParams: URLSearchParams): ModelSpecDetailStage => {
	const requestedStage = searchParams.get("activeStage");
	if (requestedStage !== null) return isModelSpecDetailStage(requestedStage) ? requestedStage : "logical";
	const legacyTab = searchParams.get("tab");
	return legacyTab === "release" || isModelSpecDetailTab(legacyTab) ? legacyTabStage[legacyTab] : "logical";
};

export const modelSpecDetailPath = (
	modelSpecId: string,
	stage: ModelSpecDetailStage | ModelSpecDetailTab,
	planId?: string | null,
): string => {
	const activeStage = (MODEL_SPEC_DETAIL_TABS as readonly string[]).includes(stage) ? legacyTabToStage(stage as ModelSpecDetailTab) : stage;
	const params = new URLSearchParams({ activeStage });
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

export const modelSpecCatalogPath = (
	modelType: ModelSpecType,
	planId?: string | null,
	domainId?: string | null,
): string => {
	const basePath = modelType === "DIMENSION" ? "/modeling/dimensions" : "/modeling/models";
	const normalizedPlanId = planId?.trim();
	if (!normalizedPlanId) return basePath;
	const params = new URLSearchParams({ planId: normalizedPlanId });
	const normalizedDomainId = domainId?.trim();
	if (modelType === "DIMENSION" && normalizedDomainId) params.set("domainId", normalizedDomainId);
	return `${basePath}?${params.toString()}`;
};
