export type SemanticModelingSection = "overview" | "subjects" | "objects" | "metrics" | "models" | "publish" | "runs";

export const semanticSectionMeta: Record<SemanticModelingSection, { title: string; path: string }> = {
	overview: { title: "语义建模流程", path: "/metrics/semantic" },
	subjects: { title: "主题域映射", path: "/metrics/semantic/subjects" },
	objects: { title: "业务对象 Join", path: "/metrics/semantic/objects" },
	metrics: { title: "指标可视化配置", path: "/metrics/semantic/metrics" },
	models: { title: "DWS/ADS 数据集", path: "/metrics/semantic/models" },
	publish: { title: "审核发布与血缘", path: "/metrics/semantic/publish" },
	runs: { title: "模型运行监控", path: "/metrics/semantic/runs" },
};

export const semanticSections = Object.keys(semanticSectionMeta) as SemanticModelingSection[];

export const isDwdSemanticInput = (item: { layer?: string; warehouseLayer?: string; table?: string; name?: string }) => {
	const layer = String(item.warehouseLayer || item.layer || "").toUpperCase();
	const table = String(item.table || item.name || "").toLowerCase();
	return layer === "DWD" || table.startsWith("dwd_");
};

export const isConsumableSemanticModel = (item: { type?: string }) => {
	const type = String(item.type || "").toUpperCase();
	return type === "DWS" || type === "ADS";
};

export const asArray = <T,>(payload: any): T[] => {
	if (Array.isArray(payload)) return payload;
	if (Array.isArray(payload?.content)) return payload.content;
	if (Array.isArray(payload?.data)) return payload.data;
	if (Array.isArray(payload?.data?.content)) return payload.data.content;
	return [];
};
