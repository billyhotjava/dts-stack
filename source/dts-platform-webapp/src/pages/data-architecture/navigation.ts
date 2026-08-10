import type { DataModelingRoute } from "@/pages/data-modeling/types";

export const DATA_ARCHITECTURE_VIEWS = ["business-domains", "processes", "layers", "marts", "subjects"] as const;

export type DataArchitectureView = (typeof DATA_ARCHITECTURE_VIEWS)[number];

const MODELING_PLANNING_VIEWS = ["business-categories", "layers", "domains", "processes", "marts", "subjects"] as const;

export type ModelingPlanningView = (typeof MODELING_PLANNING_VIEWS)[number];

export type DataArchitectureNavigation = {
	surface: "modeling" | "architecture";
	activeView: string;
};

const VIEW_ROUTES: Record<DataArchitectureView, Pick<DataModelingRoute, "title" | "description">> = {
	"business-domains": {
		title: "业务分类与数据域",
		description: "维护平台全局业务分类和数据域，供建模、资产、指标与质量统一引用。",
	},
	processes: {
		title: "业务过程",
		description: "维护已归属数据域的业务活动与分析过程。",
	},
	layers: {
		title: "数仓分层",
		description: "查看平台统一的贴源层、公共层和应用层分层规范。",
	},
	marts: {
		title: "数据集市",
		description: "维护面向消费场景的数据集市及其业务分类归属。",
	},
	subjects: {
		title: "主题域",
		description: "维护数据集市下的主题域与用途说明。",
	},
};

const LEGACY_PLANNING_VIEW: Record<string, DataArchitectureView> = {
	"business-categories": "business-domains",
	domains: "business-domains",
	processes: "processes",
	layers: "layers",
	marts: "marts",
	subjects: "subjects",
};

const DEFAULT_MODELING_VIEW: Record<DataArchitectureView, ModelingPlanningView> = {
	"business-domains": "business-categories",
	processes: "processes",
	layers: "layers",
	marts: "marts",
	subjects: "subjects",
};

const withLocationState = (pathname: string, params: URLSearchParams, hash: string) => {
	const search = params.toString();
	return `${pathname}${search ? `?${search}` : ""}${hash || ""}`;
};

export function resolveDataArchitectureView(value: string | null | undefined): DataArchitectureView {
	return DATA_ARCHITECTURE_VIEWS.includes(value as DataArchitectureView)
		? (value as DataArchitectureView)
		: "business-domains";
}

export function dataArchitectureRoute(view: DataArchitectureView): DataModelingRoute {
	return {
		workspace: "planning",
		view,
		...VIEW_ROUTES[view],
	};
}

export function dataArchitecturePath(view: DataArchitectureView, activeId?: string | null) {
	const params = new URLSearchParams({ view });
	if (activeId?.trim()) params.set("active", activeId.trim());
	return `/data-architecture?${params.toString()}`;
}

export function modelingSpaceDataArchitecturePath(
	view: DataArchitectureView,
	planningView: ModelingPlanningView,
	activeId?: string | null,
) {
	const params = new URLSearchParams({ view, source: "modeling-space", planningView });
	if (activeId?.trim()) params.set("active", activeId.trim());
	return `/data-architecture?${params.toString()}`;
}

export function resolveDataArchitectureNavigation(
	source: string | null | undefined,
	planningView: string | null | undefined,
	view: DataArchitectureView,
): DataArchitectureNavigation {
	if (source !== "modeling-space") return { surface: "architecture", activeView: view };
	const activeView = MODELING_PLANNING_VIEWS.includes(planningView as ModelingPlanningView)
		? (planningView as ModelingPlanningView)
		: DEFAULT_MODELING_VIEW[view];
	return { surface: "modeling", activeView };
}

export function legacyPlanningArchitectureTarget(pathname: string, search = "", hash = ""): string | null {
	const prefix = "/data-modeling/planning/";
	if (!pathname.startsWith(prefix)) return null;
	const leaf = pathname.slice(prefix.length).split("/")[0] || "";
	const view = LEGACY_PLANNING_VIEW[leaf];
	if (!view) return null;
	const params = new URLSearchParams(search);
	params.set("view", view);
	if (leaf === "processes" && params.get("domain") && !params.get("active")) {
		params.set("active", params.get("domain") || "");
	}
	return withLocationState("/data-architecture", params, hash);
}

export function legacySubjectAreasTarget(search = "", hash = "") {
	const params = new URLSearchParams(search);
	const tab = params.get("tab") || "scope";
	const active = params.get("active") || params.get("domainId") || "";
	if (tab === "data-marts") {
		params.set("view", "marts");
		if (active) params.set("businessCategoryId", active);
		return withLocationState("/data-architecture", params, hash);
	}
	if (tab === "details") {
		params.set("view", "subjects");
		if (active) {
			params.set("businessCategoryId", active);
			params.set("filter", active);
		}
		return withLocationState("/data-architecture", params, hash);
	}
	if (tab === "governance") {
		if (active) params.set("domain", active);
		return withLocationState("/catalog/assets", params, hash);
	}
	params.set("view", "baseline");
	params.set("tab", "categories");
	if (active) params.set("domainId", active);
	return withLocationState("/data-modeling/planning/spaces", params, hash);
}
