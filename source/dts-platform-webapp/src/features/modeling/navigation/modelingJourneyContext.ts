import {
	type BusinessModelingContext,
	buildBusinessModelingRoute,
	resolveBusinessModelingContext,
} from "./businessModelingContext";

export type ModelingStage = "SCOPE" | "LOGICAL" | "IMPLEMENTATION" | "RELEASE";
export type ModelingMethod = "RELATIONAL" | "DIMENSIONAL" | "DBT_NATIVE";
export type ModelingScopeKind = "DOMAIN" | "PROCESS" | "OBJECT" | "MODEL";

export type ModelingJourneyContext = BusinessModelingContext & {
	scopeId?: string;
	scopeKind?: ModelingScopeKind;
	modelId?: string;
	method?: ModelingMethod;
	stage: ModelingStage;
};

// Controlled-retirement rule: planId is inherited from BusinessModelingContext and is the
// only canonical WarehousePlan identifier. The remaining scope fields are read-only legacy context.

const stageFrom = (value?: string | null): ModelingStage => {
	switch (value?.trim().toLowerCase()) {
		case "logical":
			return "LOGICAL";
		case "implementation":
			return "IMPLEMENTATION";
		case "release":
			return "RELEASE";
		default:
			return "SCOPE";
	}
};

const scopeKindFrom = (value?: string | null): ModelingScopeKind | undefined => {
	switch (value?.trim().toLowerCase()) {
		case "domain":
			return "DOMAIN";
		case "process":
			return "PROCESS";
		case "object":
			return "OBJECT";
		case "model":
			return "MODEL";
		default:
			return undefined;
	}
};

const methodFrom = (value?: string | null, modelingMode?: string): ModelingMethod | undefined => {
	switch (value?.trim().toLowerCase()) {
		case "relational":
			return "RELATIONAL";
		case "dimensional":
			return "DIMENSIONAL";
		case "dbt_native":
		case "dbt-native":
			return "DBT_NATIVE";
		default:
			return modelingMode === "dimension" ? "DIMENSIONAL" : undefined;
	}
};

export const modelingStagePath = (stage: ModelingStage): string =>
	({
		SCOPE: "/data-modeling/planning/spaces",
		LOGICAL: "/data-modeling/dimensions/workbench",
		IMPLEMENTATION: "/data-modeling/dimensions/workbench?mode=implementation",
		RELEASE: "/data-modeling/home/workspace?view=release",
	})[stage];

export const resolveModelingJourneyContext = (searchParams: URLSearchParams): ModelingJourneyContext => {
	const legacy = resolveBusinessModelingContext(searchParams);
	const domainId = searchParams.get("domainId")?.trim() || searchParams.get("active")?.trim() || legacy.domainId;
	const modelId = searchParams.get("modelId")?.trim() || legacy.modelSpecId;
	const scopeId = searchParams.get("scopeId")?.trim() || legacy.processId || legacy.objectId || modelId;
	const scopeKind =
		scopeKindFrom(searchParams.get("scopeKind")) ||
		(legacy.processId ? "PROCESS" : legacy.objectId ? "OBJECT" : modelId ? "MODEL" : domainId ? "DOMAIN" : undefined);
	return {
		...legacy,
		domainId,
		scopeId,
		scopeKind,
		modelId,
		method: methodFrom(
			searchParams.get("method"),
			legacy.modelingMode || (searchParams.has("focus") ? "dimension" : undefined),
		),
		stage: stageFrom(searchParams.get("stage")),
	};
};

export const buildModelingJourneyRoute = (route: string, context: Partial<ModelingJourneyContext>): string => {
	return buildBusinessModelingRoute(route, {
		...context,
		modelSpecId: context.modelSpecId || context.modelId,
	});
};
