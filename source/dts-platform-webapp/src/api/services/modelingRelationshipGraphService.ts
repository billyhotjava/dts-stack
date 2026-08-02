import {
	getWarehousePlanRelationshipGraph,
	listWarehousePlans,
	type WarehousePlanHeader,
	type WarehousePlanRelationshipGraph,
	type WarehousePlanRelationshipGraphKind,
} from "../warehousePlanApi";

const GRAPH_PAGE_SIZE = 40;

export type ModelingRelationshipGraphFailure = {
	kind: "permission" | "request";
	message: string;
};

export type ModelingRelationshipGraphQuery = {
	view: string;
	query?: string;
	cursor?: string;
};

const kindForView = (view: string): WarehousePlanRelationshipGraphKind | undefined => {
	if (view === "standards") return "STANDARD";
	if (view === "metrics") return "INDICATOR";
	return undefined;
};

export const listModelingRelationshipPlans = (): Promise<WarehousePlanHeader[]> => listWarehousePlans();

export const loadModelingRelationshipGraph = (
	planId: string,
	query: ModelingRelationshipGraphQuery,
): Promise<WarehousePlanRelationshipGraph> =>
	getWarehousePlanRelationshipGraph(planId, {
		kind: kindForView(query.view),
		query: query.query?.trim() || undefined,
		limit: GRAPH_PAGE_SIZE,
		cursor: query.cursor || undefined,
	});

export function classifyModelingRelationshipGraphFailure(error: unknown): ModelingRelationshipGraphFailure {
	const status = Number((error as { response?: { status?: unknown } } | null)?.response?.status ?? 0);
	if (status === 401 || status === 403) {
		return { kind: "permission", message: "当前账号无权访问该建设计划的关系图，请联系管理员授权。" };
	}
	const detail = error instanceof Error ? error.message.trim() : "";
	return {
		kind: "request",
		message: detail || "关系图读取失败，请稍后重新加载。",
	};
}

export function modelingRelationshipNodePath(
	node: Pick<WarehousePlanRelationshipGraph["nodes"][number], "kind" | "route">,
): string | null {
	const raw = node.route?.trim();
	if (!raw) return null;
	if (raw.startsWith("/data-modeling/")) return raw;

	let legacy: URL;
	try {
		legacy = new URL(raw, "http://dts.local");
	} catch {
		return null;
	}
	if (legacy.origin !== "http://dts.local" || legacy.pathname !== "/modeling/workbench") return null;

	const planId = legacy.searchParams.get("planId");
	const assetId = legacy.searchParams.get("assetId");
	const revision = legacy.searchParams.get("revision");
	const version = legacy.searchParams.get("version");
	const params = new URLSearchParams();
	if (planId) params.set("planId", planId);

	let pathname: string;
	switch (node.kind) {
		case "PLAN":
			pathname = "/data-modeling/planning/spaces";
			break;
		case "MODEL":
			pathname = "/data-modeling/dimensions/workbench";
			if (assetId) params.set("modelSpecId", assetId);
			if (revision) params.set("revision", revision);
			break;
		case "DIMENSION":
			pathname = "/data-modeling/dimensions/workbench";
			if (assetId) params.set("dimensionDefinitionId", assetId);
			if (revision) params.set("revision", revision);
			break;
		case "STANDARD":
			pathname = "/data-modeling/standards/fields";
			if (assetId) params.set("standardId", assetId);
			if (version) params.set("version", version);
			break;
		case "INDICATOR":
			pathname = "/data-modeling/metrics/atomic";
			if (assetId) params.set("indicatorId", assetId);
			break;
		default:
			return null;
	}
	const search = params.toString();
	return search ? `${pathname}?${search}` : pathname;
}

export type {
	WarehousePlanHeader,
	WarehousePlanRelationshipGraph,
	WarehousePlanRelationshipGraphEdge,
	WarehousePlanRelationshipGraphKind,
	WarehousePlanRelationshipGraphNode,
} from "../warehousePlanApi";
