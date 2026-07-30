export const MODELING_WORKSPACE_MODULES = [
	"home",
	"planning",
	"standards",
	"models",
	"metrics",
	"tools",
	"graph",
] as const;

export type ModelingWorkspaceModule = (typeof MODELING_WORKSPACE_MODULES)[number];

export const MODELING_WORKSPACE_VIEWS = [
	"overview",
	"categories",
	"data-marts",
	"layers",
	"sources",
	"elements",
	"reference",
	"glossary",
	"units",
	"dimensions",
	"model-specs",
	"definitions",
	"utilities",
	"relationships",
] as const;

export type ModelingWorkspaceView = (typeof MODELING_WORKSPACE_VIEWS)[number];

export const MODELING_WORKSPACE_ASSET_KINDS = [
	"plan",
	"dimension",
	"model",
	"indicator",
	"standard",
	"source",
] as const;

export type ModelingWorkspaceAssetKind = (typeof MODELING_WORKSPACE_ASSET_KINDS)[number];

export const MODELING_WORKSPACE_MODEL_STAGES = ["logical", "implementation", "physical"] as const;

export type ModelingWorkspaceModelStage = (typeof MODELING_WORKSPACE_MODEL_STAGES)[number];

export type ModelingWorkspaceRouteState = {
	module: ModelingWorkspaceModule;
	workspaceView?: ModelingWorkspaceView;
	planId?: string;
	assetKind?: ModelingWorkspaceAssetKind;
	assetId?: string;
	activeStage?: ModelingWorkspaceModelStage;
};

export type ModelingWorkspaceRoutePatch = {
	module?: ModelingWorkspaceModule | string | null;
	workspaceView?: ModelingWorkspaceView | string | null;
	planId?: string | null;
	assetKind?: ModelingWorkspaceAssetKind | string | null;
	assetId?: string | null;
	activeStage?: ModelingWorkspaceModelStage | string | null;
};

export const isModelingWorkspaceModelAssetOpen = (state: ModelingWorkspaceRouteState): boolean =>
	state.module === "models" &&
	state.workspaceView === "model-specs" &&
	state.assetKind === "model" &&
	Boolean(state.assetId);

const moduleSet = new Set<string>(MODELING_WORKSPACE_MODULES);
const assetKindSet = new Set<string>(MODELING_WORKSPACE_ASSET_KINDS);
const modelStageSet = new Set<string>(MODELING_WORKSPACE_MODEL_STAGES);
const viewSetByModule: Record<ModelingWorkspaceModule, ReadonlySet<string>> = {
	home: new Set(),
	planning: new Set(["overview", "categories", "data-marts", "layers", "sources"]),
	standards: new Set(["elements", "reference", "glossary", "units"]),
	models: new Set(["dimensions", "model-specs"]),
	metrics: new Set(["definitions"]),
	tools: new Set(["utilities"]),
	graph: new Set(["relationships"]),
};

const text = (value: string | null | undefined) => value?.trim() || undefined;

const workspaceModule = (value: string | null | undefined): ModelingWorkspaceModule => {
	const normalized = text(value);
	return normalized && moduleSet.has(normalized) ? (normalized as ModelingWorkspaceModule) : "home";
};

const workspaceView = (
	value: string | null | undefined,
	module: ModelingWorkspaceModule,
): ModelingWorkspaceView | undefined => {
	const normalized = text(value);
	return normalized && viewSetByModule[module].has(normalized) ? (normalized as ModelingWorkspaceView) : undefined;
};

const assetKind = (value: string | null | undefined): ModelingWorkspaceAssetKind | undefined => {
	const normalized = text(value);
	return normalized && assetKindSet.has(normalized) ? (normalized as ModelingWorkspaceAssetKind) : undefined;
};

const modelStage = (value: string | null | undefined): ModelingWorkspaceModelStage | undefined => {
	const normalized = text(value);
	return normalized && modelStageSet.has(normalized) ? (normalized as ModelingWorkspaceModelStage) : undefined;
};

export const parseModelingWorkspaceRouteState = (searchParams: URLSearchParams): ModelingWorkspaceRouteState => {
	const module = workspaceModule(searchParams.get("module"));
	const state: ModelingWorkspaceRouteState = {
		module,
	};
	const nextView = workspaceView(searchParams.get("workspaceView"), module);
	if (nextView) state.workspaceView = nextView;

	const planId = text(searchParams.get("planId"));
	if (planId) state.planId = planId;

	const nextAssetKind = assetKind(searchParams.get("assetKind"));
	const assetId = text(searchParams.get("assetId"));
	if (nextAssetKind && assetId) {
		state.assetKind = nextAssetKind;
		state.assetId = assetId;
		const activeStage = nextAssetKind === "model" ? modelStage(searchParams.get("activeStage")) : undefined;
		if (activeStage) state.activeStage = activeStage;
	}
	return state;
};

const has = (patch: ModelingWorkspaceRoutePatch, key: keyof ModelingWorkspaceRoutePatch) => Object.hasOwn(patch, key);

const assign = (searchParams: URLSearchParams, key: string, value: string | undefined) => {
	if (value) {
		searchParams.set(key, value);
	} else {
		searchParams.delete(key);
	}
};

export const updateModelingWorkspaceSearch = (current: URLSearchParams, patch: ModelingWorkspaceRoutePatch): string => {
	const next = new URLSearchParams(current);
	const currentState = parseModelingWorkspaceRouteState(current);

	if (has(patch, "module")) {
		next.set("module", workspaceModule(patch.module));
	}

	const nextModule = workspaceModule(next.get("module"));
	if (has(patch, "workspaceView")) {
		assign(next, "workspaceView", workspaceView(patch.workspaceView, nextModule));
	} else if (has(patch, "module")) {
		assign(next, "workspaceView", workspaceView(current.get("workspaceView"), nextModule));
	}

	if (has(patch, "planId")) {
		const nextPlanId = text(patch.planId);
		assign(next, "planId", nextPlanId);
		if (nextPlanId !== currentState.planId && !has(patch, "assetKind") && !has(patch, "assetId")) {
			next.delete("assetKind");
			next.delete("assetId");
			next.delete("activeStage");
		}
	}

	if (has(patch, "assetKind") || has(patch, "assetId")) {
		const nextAssetKind = has(patch, "assetKind") ? assetKind(patch.assetKind) : currentState.assetKind;
		const nextAssetId = has(patch, "assetId") ? text(patch.assetId) : currentState.assetId;
		if (nextAssetKind && nextAssetId) {
			next.set("assetKind", nextAssetKind);
			next.set("assetId", nextAssetId);
		} else {
			next.delete("assetKind");
			next.delete("assetId");
		}
	}

	if (has(patch, "activeStage")) {
		assign(next, "activeStage", modelStage(patch.activeStage));
	}
	if (next.get("assetKind") !== "model" || !text(next.get("assetId"))) {
		next.delete("activeStage");
	}

	return next.toString();
};
