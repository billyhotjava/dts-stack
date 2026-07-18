import type {
	WarehousePlanEvidenceFreshness,
	WarehousePlanStageCode,
	WarehousePlanStageStatus,
} from "@/api/warehousePlanApi";

export const WAREHOUSE_STAGE_ORDER: readonly WarehousePlanStageCode[] = [
	"DATA_CONNECTION",
	"SOURCE_INVENTORY",
	"WAREHOUSE_PLANNING",
	"DATA_STANDARD",
	"MODEL_DESIGN",
	"BUILD_QUALITY_RELEASE",
	"DATA_ASSET",
	"METRIC_SYSTEM",
	"DATA_SERVICE_OPERATIONS",
] as const;

const STAGE_LABELS: Record<WarehousePlanStageCode, string> = {
	DATA_CONNECTION: "数据连接",
	SOURCE_INVENTORY: "来源盘点",
	WAREHOUSE_PLANNING: "数仓规划",
	DATA_STANDARD: "数据标准",
	MODEL_DESIGN: "事实与维度设计",
	BUILD_QUALITY_RELEASE: "构建、质量与发布",
	DATA_ASSET: "数据资产",
	METRIC_SYSTEM: "指标系统",
	DATA_SERVICE_OPERATIONS: "数据服务与运维",
};

const STAGE_ACTION_LABELS: Record<WarehousePlanStageCode, string> = {
	DATA_CONNECTION: "检查数据连接",
	SOURCE_INVENTORY: "继续来源盘点",
	WAREHOUSE_PLANNING: "完善数仓规划",
	DATA_STANDARD: "绑定数据标准",
	MODEL_DESIGN: "继续模型设计",
	BUILD_QUALITY_RELEASE: "检查构建与发布门禁",
	DATA_ASSET: "查看资产登记",
	METRIC_SYSTEM: "检查指标绑定",
	DATA_SERVICE_OPERATIONS: "查看服务与运行",
};

const BLOCKER_MESSAGES: Record<string, string> = {
	BUSINESS_SCOPE_INCOMPLETE: "业务范围尚未确认",
	DOMAIN_PROCESS_CONFIRMATION_INCOMPLETE: "主题域和业务过程尚未全部确认",
	SOURCE_INVENTORY_INCOMPLETE: "来源盘点尚未完成",
	SOURCE_BUSINESS_MAPPING_INCOMPLETE: "来源与业务范围尚未全部映射或排除",
	PLANNING_POLICY_INCOMPLETE: "分层、命名或历史策略尚未补齐",
	EVIDENCE_STALE: "完成证据已过期，需要重新核验",
	EVIDENCE_UNAVAILABLE: "完成证据暂时不可用",
};

export const warehouseStageLabel = (code: WarehousePlanStageCode): string => STAGE_LABELS[code];

export const warehouseStageActionLabel = (code: WarehousePlanStageCode): string => STAGE_ACTION_LABELS[code];

export const warehouseBlockerMessage = (code: string, fallback?: string | null): string => {
	if (BLOCKER_MESSAGES[code]) return BLOCKER_MESSAGES[code];
	if (code.endsWith("_NOT_STARTED")) return "本阶段尚未产生可核验的完成证据";
	if (code.endsWith("_UNKNOWN")) return "本阶段的完成证据暂时无法核验";
	if (code.endsWith("_IN_PROGRESS")) return "本阶段仍在进行，尚未形成完整证据";
	if (code.endsWith("_BLOCKED")) return "本阶段存在尚未处理的阻塞";
	if (fallback && /[\u3400-\u9fff]/.test(fallback)) return fallback;
	return "本阶段存在尚未处理的阻塞";
};

export const stageStatusLabel = (
	status: WarehousePlanStageStatus,
	freshness: WarehousePlanEvidenceFreshness,
): string => {
	if (freshness === "STALE") return "证据已过期";
	if (status === "UNKNOWN" || freshness === "UNAVAILABLE") return "证据未知";
	return (
		{
			NOT_STARTED: "未开始",
			IN_PROGRESS: "进行中",
			BLOCKED: "有阻塞",
			COMPLETE: "已完成",
			UNKNOWN: "证据未知",
		} satisfies Record<WarehousePlanStageStatus, string>
	)[status];
};

export const isWarehouseStageComplete = (
	status: WarehousePlanStageStatus,
	freshness: WarehousePlanEvidenceFreshness,
): boolean => status === "COMPLETE" && freshness === "CURRENT";

export const buildWarehousePlanRoute = (
	planId: string,
	section?: "overview" | "baseline" | "architecture" | "models" | "implementation" | "deliverables",
	query: Record<string, string | null | undefined> = {},
): string => {
	const suffix = !section || section === "overview" ? "" : `/${section}`;
	const params = new URLSearchParams();
	for (const [key, value] of Object.entries(query)) {
		if (value) params.set(key, value);
	}
	params.set("planId", planId);
	return `/modeling/plans/${planId}${suffix}?${params.toString()}`;
};

export const withWarehousePlanContext = (route: string, planId: string): string => {
	const [path, query = ""] = route.split("?");
	const params = new URLSearchParams(query);
	params.set("planId", planId);
	return `${path}?${params.toString()}`;
};
