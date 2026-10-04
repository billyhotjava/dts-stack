import {
	getWarehousePlanRelationshipGraph,
	listWarehousePlans,
	type WarehousePlanHeader,
	type WarehousePlanRelationshipGraph,
	type WarehousePlanRelationshipGraphEdge,
	type WarehousePlanRelationshipGraphKind,
	type WarehousePlanRelationshipGraphNode,
} from "../warehousePlanApi";

const GRAPH_PAGE_SIZE = 500;

export type ModelingRelationshipGraphFailure = {
	kind: "permission" | "request";
	message: string;
};

export type ModelingRelationshipGraphQuery = {
	view: string;
	query?: string;
	cursor?: string;
};

export type ModelingRelationshipContextHeader = WarehousePlanHeader;
export type ModelingRelationshipGraph = WarehousePlanRelationshipGraph;
export type ModelingRelationshipGraphEdge = WarehousePlanRelationshipGraphEdge;
export type ModelingRelationshipGraphKind = WarehousePlanRelationshipGraphKind;
export type ModelingRelationshipGraphNode = WarehousePlanRelationshipGraphNode;

export const listModelingRelationshipPlans = (): Promise<ModelingRelationshipContextHeader[]> => listWarehousePlans();

export const loadModelingRelationshipGraph = (
	planId: string,
	_query: ModelingRelationshipGraphQuery,
): Promise<ModelingRelationshipGraph> =>
	getWarehousePlanRelationshipGraph(planId, {
		limit: GRAPH_PAGE_SIZE,
	});

export function classifyModelingRelationshipGraphFailure(error: unknown): ModelingRelationshipGraphFailure {
	const status = Number((error as { response?: { status?: unknown } } | null)?.response?.status ?? 0);
	if (status === 401 || status === 403) {
		return { kind: "permission", message: "当前账号无权访问模型关系图，请联系管理员授权。" };
	}
	return {
		kind: "request",
		message: "关系图读取失败，请稍后重新加载。",
	};
}

export function modelingRelationshipNodePath(
	node: Pick<ModelingRelationshipGraph["nodes"][number], "kind" | "route">,
): string | null {
	if (node.kind !== "MODEL" && node.kind !== "INDICATOR") return null;
	const raw = node.route?.trim();
	if (!raw) return null;
	if (raw.startsWith("/data-modeling/")) {
		if (node.kind === "MODEL" && raw.startsWith("/data-modeling/dimensions/workbench")) return raw;
		if (node.kind === "INDICATOR" && raw.startsWith("/data-modeling/metrics/")) return raw;
		return null;
	}

	let legacy: URL;
	try {
		legacy = new URL(raw, "http://dts.local");
	} catch {
		return null;
	}
	if (legacy.origin !== "http://dts.local" || legacy.pathname !== "/modeling/workbench") return null;

	const assetId = legacy.searchParams.get("assetId");
	const revision = legacy.searchParams.get("revision");
	const params = new URLSearchParams();

	let pathname: string;
	switch (node.kind) {
		case "MODEL":
			pathname = "/data-modeling/dimensions/workbench";
			if (assetId) params.set("modelSpecId", assetId);
			if (revision) params.set("revision", revision);
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
