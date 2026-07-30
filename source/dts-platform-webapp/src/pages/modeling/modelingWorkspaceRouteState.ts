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

export const MODELING_WORKSPACE_ASSET_KINDS = [
	"plan",
	"dimension",
	"model",
	"indicator",
	"standard",
	"source",
] as const;

export type ModelingWorkspaceAssetKind = (typeof MODELING_WORKSPACE_ASSET_KINDS)[number];

export type ModelingWorkspaceRouteState = {
	module: ModelingWorkspaceModule;
	planId?: string;
	assetKind?: ModelingWorkspaceAssetKind;
	assetId?: string;
};

export type ModelingWorkspaceRoutePatch = {
	module?: ModelingWorkspaceModule | string | null;
	planId?: string | null;
	assetKind?: ModelingWorkspaceAssetKind | string | null;
	assetId?: string | null;
};

const moduleSet = new Set<string>(MODELING_WORKSPACE_MODULES);
const assetKindSet = new Set<string>(MODELING_WORKSPACE_ASSET_KINDS);

const text = (value: string | null | undefined) => value?.trim() || undefined;

const workspaceModule = (value: string | null | undefined): ModelingWorkspaceModule => {
	const normalized = text(value);
	return normalized && moduleSet.has(normalized) ? (normalized as ModelingWorkspaceModule) : "home";
};

const assetKind = (value: string | null | undefined): ModelingWorkspaceAssetKind | undefined => {
	const normalized = text(value);
	return normalized && assetKindSet.has(normalized) ? (normalized as ModelingWorkspaceAssetKind) : undefined;
};

export const parseModelingWorkspaceRouteState = (searchParams: URLSearchParams): ModelingWorkspaceRouteState => {
	const state: ModelingWorkspaceRouteState = {
		module: workspaceModule(searchParams.get("module")),
	};
	const planId = text(searchParams.get("planId"));
	if (planId) state.planId = planId;

	const nextAssetKind = assetKind(searchParams.get("assetKind"));
	const assetId = text(searchParams.get("assetId"));
	if (nextAssetKind && assetId) {
		state.assetKind = nextAssetKind;
		state.assetId = assetId;
	}
	return state;
};

const has = (patch: ModelingWorkspaceRoutePatch, key: keyof ModelingWorkspaceRoutePatch) =>
	Object.prototype.hasOwnProperty.call(patch, key);

const assign = (searchParams: URLSearchParams, key: string, value: string | undefined) => {
	if (value) {
		searchParams.set(key, value);
	} else {
		searchParams.delete(key);
	}
};

export const updateModelingWorkspaceSearch = (
	current: URLSearchParams,
	patch: ModelingWorkspaceRoutePatch,
): string => {
	const next = new URLSearchParams(current);
	const currentState = parseModelingWorkspaceRouteState(current);

	if (has(patch, "module")) {
		next.set("module", workspaceModule(patch.module));
	}

	if (has(patch, "planId")) {
		const nextPlanId = text(patch.planId);
		assign(next, "planId", nextPlanId);
		if (nextPlanId !== currentState.planId && !has(patch, "assetKind") && !has(patch, "assetId")) {
			next.delete("assetKind");
			next.delete("assetId");
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

	return next.toString();
};
