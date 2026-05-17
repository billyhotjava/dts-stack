export type SemanticModelingSection = "overview" | "subjects" | "objects" | "metrics" | "models" | "publish" | "runs";

export const semanticSectionMeta: Record<SemanticModelingSection, { title: string; path: string }> = {
	overview: { title: "语义建模流程", path: "/bi-apps/metrics/semantic" },
	subjects: { title: "主题域映射", path: "/bi-apps/metrics/semantic/subjects" },
	objects: { title: "业务对象 Join", path: "/bi-apps/metrics/semantic/objects" },
	metrics: { title: "指标可视化配置", path: "/bi-apps/metrics/semantic/metrics" },
	models: { title: "DWS/ADS 数据集", path: "/bi-apps/metrics/semantic/models" },
	publish: { title: "审核发布与血缘", path: "/bi-apps/metrics/publish" },
	runs: { title: "模型运行监控", path: "/bi-apps/metrics/semantic/runs" },
};

export const semanticSections = Object.keys(semanticSectionMeta) as SemanticModelingSection[];

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
